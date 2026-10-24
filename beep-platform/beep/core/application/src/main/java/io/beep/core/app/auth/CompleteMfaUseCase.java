package io.beep.core.app.auth;

import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Second facteur après un lien magique ou OAuth : le jeton n'est consommé qu'avec un code TOTP correct (5 essais). */
public class CompleteMfaUseCase {
  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final TokenPort tokenPort;
  private final SecretCipherPort cipher;
  private final RateLimiterPort limiter;
  private final SessionIssuer issuer;
  private final Clock clock;
  private final AuthSettings settings;

  public CompleteMfaUseCase(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, SecretCipherPort cipher, RateLimiterPort limiter,
      SessionIssuer issuer, Clock clock, AuthSettings settings) {
    this.users = users;
    this.tokens = tokens;
    this.tokenPort = tokenPort;
    this.cipher = cipher;
    this.limiter = limiter;
    this.issuer = issuer;
    this.clock = clock;
    this.settings = settings;
  }

  public LoginUseCase.Result execute(String token, String code) {
    if (token == null || token.isBlank()) throw new DomainException("invalid_token");
    String hash = tokenPort.hash(token);
    if (!limiter.tryAcquire("mfa:" + hash, settings.loginMaxAttempts(), settings.mfaTtl())) throw new DomainException("rate_limited");
    Instant now = clock.instant();
    UUID userId = tokens.find(Purpose.MFA_PENDING, hash, now).orElseThrow(() -> new DomainException("invalid_token"));
    User user = users.findById(userId).filter(User::canLogin).orElseThrow(() -> new DomainException("invalid_token"));
    if (code == null || !Totp.verify(cipher.decrypt(user.totpSecretCipher()), code.trim(), now)) throw new DomainException("invalid_totp");
    if (tokens.consume(Purpose.MFA_PENDING, hash, now).isEmpty()) throw new DomainException("invalid_token"); // déjà utilisé en parallèle
    limiter.reset("mfa:" + hash);
    return issuer.issue(user.id());
  }
}
