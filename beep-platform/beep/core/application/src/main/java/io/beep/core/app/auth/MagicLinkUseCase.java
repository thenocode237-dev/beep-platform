package io.beep.core.app.auth;

import io.beep.core.app.port.InvitationRepository;
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
import io.beep.core.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Connexion par lien magique (sans mot de passe), qui vaut aussi inscription pour une adresse invitée.
 * Demande : réponse toujours identique. Consommation : un seul usage, 15 minutes ; elle prouve l'adresse (e-mail vérifié).
 */
public class MagicLinkUseCase {
  public record Request(String email, String locale, String clientKey) {}

  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final InvitationRepository invitations;
  private final TokenPort tokenPort;
  private final MailPort mail;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final SessionIssuer issuer;
  private final Clock clock;
  private final AuthSettings settings;

  public MagicLinkUseCase(UserRepository users, OneTimeTokenRepository tokens, InvitationRepository invitations, TokenPort tokenPort, MailPort mail,
      RateLimiterPort limiter, TransactionPort tx, SessionIssuer issuer, Clock clock, AuthSettings settings) {
    this.users = users;
    this.tokens = tokens;
    this.invitations = invitations;
    this.tokenPort = tokenPort;
    this.mail = mail;
    this.limiter = limiter;
    this.tx = tx;
    this.issuer = issuer;
    this.clock = clock;
    this.settings = settings;
  }

  public void request(Request cmd) {
    if (!limiter.tryAcquire("magic:" + cmd.clientKey(), settings.signupMaxPerClient(), settings.signupWindow())) throw new DomainException("rate_limited");
    Email email;
    try {
      email = new Email(cmd.email());
    } catch (DomainException e) {
      return; // même réponse qu'une adresse inconnue
    }
    if (!limiter.tryAcquire("magic-mail:" + email, 3, settings.signupWindow())) return; // pas de bombardement d'une boîte
    Instant now = clock.instant();
    Optional<User> found = users.findByEmail(email);
    User user;
    if (found.isPresent()) {
      user = found.get();
      if (user.status() == UserStatus.SUSPENDED) return;
    } else {
      String locale = Locales.normalize(cmd.locale());
      if (settings.inviteOnly() && !Invitations.usable(invitations, email, now)) {
        mail.sendInvitationRequired(email, locale);
        return;
      }
      user = User.registerExternal(UUID.randomUUID(), email, locale, now);
      User created = user;
      try {
        tx.run(() -> {
          users.insert(created);
          invitations.consume(email, now);
        });
      } catch (UserRepository.DuplicateEmailException race) {
        return;
      }
    }
    String token = tokenPort.newToken();
    UUID userId = user.id();
    tx.run(() -> tokens.insert(userId, Purpose.MAGIC_LINK, tokenPort.hash(token), now.plus(settings.magicLinkTtl())));
    mail.sendMagicLink(email, user.locale(), token);
  }

  public AuthOutcome consume(String token) {
    if (token == null || token.isBlank()) throw new DomainException("invalid_token");
    Instant now = clock.instant();
    UUID userId = tx.run(() -> tokens.consume(Purpose.MAGIC_LINK, tokenPort.hash(token), now)).orElseThrow(() -> new DomainException("invalid_token"));
    User user = users.findById(userId).orElseThrow(() -> new DomainException("invalid_token"));
    if (user.status() == UserStatus.SUSPENDED) throw new DomainException("invalid_credentials");
    if (!user.emailVerified()) {
      user = user.verifyEmail(now);
      users.update(user);
    }
    return issuer.afterFirstFactor(user);
  }
}
