package io.beep.core.app.auth;

import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class LoginUseCase {
  public record Command(String email, String password, String totp, String clientKey) {}

  public record Result(String sessionToken, Instant expiresAt) {}

  private final UserRepository users;
  private final SessionIssuer issuer;
  private final PasswordHasherPort hasher;
  private final SecretCipherPort cipher;
  private final RateLimiterPort limiter;
  private final Clock clock;
  private final AuthSettings settings;
  private final String dummyHash; // égalise le temps de réponse quand le compte n'existe pas (ou n'a pas de mot de passe)

  public LoginUseCase(UserRepository users, SessionIssuer issuer, PasswordHasherPort hasher, SecretCipherPort cipher, RateLimiterPort limiter, Clock clock,
      AuthSettings settings) {
    this.users = users;
    this.issuer = issuer;
    this.hasher = hasher;
    this.cipher = cipher;
    this.limiter = limiter;
    this.clock = clock;
    this.settings = settings;
    this.dummyHash = hasher.hash("beep-timing-equalizer");
  }

  public Result execute(Command cmd) {
    Email email;
    try {
      email = new Email(cmd.email());
    } catch (DomainException e) {
      throw new DomainException("invalid_credentials"); // ne révèle rien sur la forme attendue
    }
    String key = "login:" + email + "|" + cmd.clientKey();
    if (!limiter.tryAcquire(key, settings.loginMaxAttempts(), settings.loginWindow())) throw new DomainException("rate_limited");

    Optional<User> found = users.findByEmail(email);
    String hash = found.map(User::passwordHash).orElse(null);
    boolean passwordOk = hasher.matches(cmd.password() == null ? "" : cmd.password(), hash == null ? dummyHash : hash) && hash != null;
    if (found.isEmpty() || !passwordOk) throw new DomainException("invalid_credentials");

    User user = found.get();
    if (user.status() == UserStatus.SUSPENDED) throw new DomainException("invalid_credentials");
    if (!user.canLogin()) throw new DomainException("email_not_verified");

    Instant now = clock.instant();
    if (user.totpEnabled()) {
      if (cmd.totp() == null || cmd.totp().isBlank()) throw new DomainException("totp_required");
      if (!Totp.verify(cipher.decrypt(user.totpSecretCipher()), cmd.totp().trim(), now)) throw new DomainException("invalid_totp");
    }

    limiter.reset(key);
    return issuer.issue(user.id());
  }
}
