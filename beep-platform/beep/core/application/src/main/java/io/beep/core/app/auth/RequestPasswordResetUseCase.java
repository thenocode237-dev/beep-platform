package io.beep.core.app.auth;

import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Demande de réinitialisation. Réponse toujours identique (l'existence du compte n'est jamais révélée). */
public class RequestPasswordResetUseCase {
  public record Command(String email, String clientKey) {}

  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final TokenPort tokenPort;
  private final MailPort mail;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final Clock clock;
  private final AuthSettings settings;

  public RequestPasswordResetUseCase(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, MailPort mail, RateLimiterPort limiter,
      TransactionPort tx, Clock clock, AuthSettings settings) {
    this.users = users;
    this.tokens = tokens;
    this.tokenPort = tokenPort;
    this.mail = mail;
    this.limiter = limiter;
    this.tx = tx;
    this.clock = clock;
    this.settings = settings;
  }

  public void execute(Command cmd) {
    if (!limiter.tryAcquire("reset:" + cmd.clientKey(), settings.signupMaxPerClient(), settings.signupWindow())) throw new DomainException("rate_limited");
    Email email;
    try {
      email = new Email(cmd.email());
    } catch (DomainException e) {
      return; // même réponse qu'un compte inexistant
    }
    Optional<User> user = users.findByEmail(email);
    if (user.isEmpty() || !user.get().canLogin()) return;

    String token = tokenPort.newToken();
    Instant expires = clock.instant().plus(settings.resetPasswordTtl());
    tx.run(() -> tokens.insert(user.get().id(), Purpose.RESET_PASSWORD, tokenPort.hash(token), expires));
    mail.sendPasswordReset(email, user.get().locale(), token);
  }
}
