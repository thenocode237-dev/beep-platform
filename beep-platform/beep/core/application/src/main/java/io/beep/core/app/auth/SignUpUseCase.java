package io.beep.core.app.auth;

import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.PasswordPolicy;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Inscription. Réponse identique que l'adresse existe ou non (anti-énumération) :
 * nouvelle adresse -> e-mail de confirmation ; compte non confirmé -> nouvel e-mail de confirmation ;
 * compte confirmé -> e-mail « vous avez déjà un compte ».
 */
public class SignUpUseCase {
  public record Command(String email, String password, String locale, String clientKey) {}

  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final PasswordHasherPort hasher;
  private final TokenPort tokenPort;
  private final MailPort mail;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final Clock clock;
  private final AuthSettings settings;

  public SignUpUseCase(UserRepository users, OneTimeTokenRepository tokens, PasswordHasherPort hasher, TokenPort tokenPort,
      MailPort mail, RateLimiterPort limiter, TransactionPort tx, Clock clock, AuthSettings settings) {
    this.users = users;
    this.tokens = tokens;
    this.hasher = hasher;
    this.tokenPort = tokenPort;
    this.mail = mail;
    this.limiter = limiter;
    this.tx = tx;
    this.clock = clock;
    this.settings = settings;
  }

  public void execute(Command cmd) {
    if (!limiter.tryAcquire("signup:" + cmd.clientKey(), settings.signupMaxPerClient(), settings.signupWindow())) {
      throw new DomainException("rate_limited");
    }
    Email email = new Email(cmd.email());
    PasswordPolicy.check(cmd.password());
    String locale = Locales.normalize(cmd.locale());
    Instant now = clock.instant();

    Optional<User> existing = users.findByEmail(email);
    if (existing.isPresent()) {
      User user = existing.get();
      if (user.emailVerified()) mail.sendAlreadyRegistered(email, user.locale());
      else sendVerification(user, now);
      return;
    }

    User user = User.register(UUID.randomUUID(), email, hasher.hash(cmd.password()), locale, now);
    try {
      tx.run(() -> users.insert(user));
    } catch (UserRepository.DuplicateEmailException race) {
      return; // inscription concurrente de la même adresse : la première gagne, réponse identique
    }
    sendVerification(user, now);
  }

  private void sendVerification(User user, Instant now) {
    String token = tokenPort.newToken();
    tx.run(() -> tokens.insert(user.id(), Purpose.VERIFY_EMAIL, tokenPort.hash(token), now.plus(settings.verifyEmailTtl())));
    mail.sendVerification(user.email(), user.locale(), token); // hors transaction : un échec SMTP ne laisse pas de jeton orphelin utile
  }
}
