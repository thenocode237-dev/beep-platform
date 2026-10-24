package io.beep.core.app.auth;

import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TokenPort;

public class LogoutUseCase {
  private final SessionRepository sessions;
  private final TokenPort tokenPort;

  public LogoutUseCase(SessionRepository sessions, TokenPort tokenPort) {
    this.sessions = sessions;
    this.tokenPort = tokenPort;
  }

  public void execute(String sessionToken) {
    if (sessionToken != null && !sessionToken.isBlank()) sessions.delete(tokenPort.hash(sessionToken));
  }
}
