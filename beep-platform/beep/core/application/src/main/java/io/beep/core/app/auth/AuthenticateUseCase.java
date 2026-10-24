package io.beep.core.app.auth;

import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.util.Optional;

/** Résout un jeton de session en utilisateur connecté. */
public class AuthenticateUseCase {
  private final SessionRepository sessions;
  private final UserRepository users;
  private final TokenPort tokenPort;
  private final Clock clock;

  public AuthenticateUseCase(SessionRepository sessions, UserRepository users, TokenPort tokenPort, Clock clock) {
    this.sessions = sessions;
    this.users = users;
    this.tokenPort = tokenPort;
    this.clock = clock;
  }

  public Optional<User> execute(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) return Optional.empty();
    return sessions.findActive(tokenPort.hash(sessionToken), clock.instant()).flatMap(s -> users.findById(s.userId())).filter(User::canLogin);
  }
}
