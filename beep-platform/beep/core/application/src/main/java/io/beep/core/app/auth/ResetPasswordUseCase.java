package io.beep.core.app.auth;

import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.PasswordPolicy;
import java.time.Clock;
import java.util.UUID;

/** Réinitialisation : consomme le jeton, change le mot de passe et révoque toutes les sessions existantes. */
public class ResetPasswordUseCase {
  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final SessionRepository sessions;
  private final PasswordHasherPort hasher;
  private final TokenPort tokenPort;
  private final TransactionPort tx;
  private final Clock clock;

  public ResetPasswordUseCase(UserRepository users, OneTimeTokenRepository tokens, SessionRepository sessions, PasswordHasherPort hasher,
      TokenPort tokenPort, TransactionPort tx, Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.sessions = sessions;
    this.hasher = hasher;
    this.tokenPort = tokenPort;
    this.tx = tx;
    this.clock = clock;
  }

  public void execute(String token, String newPassword) {
    PasswordPolicy.check(newPassword);
    if (token == null || token.isBlank()) throw new DomainException("invalid_token");
    String newHash = hasher.hash(newPassword);
    tx.run(() -> {
      UUID userId = tokens.consume(Purpose.RESET_PASSWORD, tokenPort.hash(token), clock.instant()).orElseThrow(() -> new DomainException("invalid_token"));
      users.findById(userId).ifPresent(u -> users.update(u.withPasswordHash(newHash)));
      sessions.deleteAllForUser(userId);
    });
  }
}
