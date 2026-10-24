package io.beep.bo.app.auth;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.PasswordHasherPort;
import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.app.port.StaffSessionRepository;
import io.beep.bo.app.port.StaffSessionRepository.Session;
import io.beep.bo.app.port.TokenPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Connexion staff : mot de passe ET TOTP exigés en une seule étape. Toute cause d'échec produit la même erreur
 * (aucune indication sur ce qui était faux) et chaque tentative est auditée.
 */
public class StaffLoginUseCase {
  public record Command(String email, String password, String totp, String ip) {}

  public record Result(String sessionToken, Instant expiresAt) {}

  private final StaffRepository staff;
  private final StaffSessionRepository sessions;
  private final PasswordHasherPort hasher;
  private final TokenPort tokenPort;
  private final SecretCipherPort cipher;
  private final RateLimiterPort limiter;
  private final AuditPort audit;
  private final Clock clock;
  private final StaffSettings settings;
  private final String dummyHash;

  public StaffLoginUseCase(StaffRepository staff, StaffSessionRepository sessions, PasswordHasherPort hasher, TokenPort tokenPort, SecretCipherPort cipher,
      RateLimiterPort limiter, AuditPort audit, Clock clock, StaffSettings settings) {
    this.staff = staff;
    this.sessions = sessions;
    this.hasher = hasher;
    this.tokenPort = tokenPort;
    this.cipher = cipher;
    this.limiter = limiter;
    this.audit = audit;
    this.clock = clock;
    this.settings = settings;
    this.dummyHash = hasher.hash("beep-timing-equalizer");
  }

  public Result execute(Command cmd) {
    Instant now = clock.instant();
    Optional<StaffUser> found = parse(cmd.email()).flatMap(staff::findByEmail);
    String key = "staff-login:" + cmd.email() + "|" + cmd.ip();

    if (!limiter.tryAcquire(key, settings.loginMaxAttempts(), settings.loginWindow())) {
      audit.append(new AuditEvent(now, found.map(StaffUser::id).orElse(null), cmd.email(), "staff.login_throttled", null, null, cmd.ip()));
      throw new DomainException("rate_limited");
    }

    boolean passwordOk = hasher.matches(cmd.password() == null ? "" : cmd.password(), found.map(StaffUser::passwordHash).orElse(dummyHash));
    boolean ok = found.isPresent() && passwordOk && found.get().canLogin() && Totp.verify(cipher.decrypt(found.get().totpSecretCipher()), cmd.totp(), now);
    if (!ok) {
      audit.append(new AuditEvent(now, found.map(StaffUser::id).orElse(null), cmd.email(), "staff.login_failed", null, null, cmd.ip()));
      throw new DomainException("invalid_credentials");
    }

    limiter.reset(key);
    StaffUser user = found.get();
    String token = tokenPort.newToken();
    Instant expires = now.plus(settings.sessionTtl());
    sessions.insert(new Session(UUID.randomUUID(), user.id(), tokenPort.hash(token), now, now, expires));
    audit.append(new AuditEvent(now, user.id(), user.email().value(), "staff.login", null, null, cmd.ip()));
    return new Result(token, expires);
  }

  private static Optional<Email> parse(String raw) {
    try {
      return Optional.of(new Email(raw));
    } catch (DomainException e) {
      return Optional.empty();
    }
  }
}
