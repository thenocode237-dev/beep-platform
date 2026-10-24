package io.beep.core.app.auth;

import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.SessionRepository.Session;
import io.beep.core.app.port.TokenPort;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Émission des sessions et des jetons de second facteur, commune à tous les moyens de connexion. */
public class SessionIssuer {
  private final SessionRepository sessions;
  private final OneTimeTokenRepository tokens;
  private final TokenPort tokenPort;
  private final Clock clock;
  private final AuthSettings settings;

  public SessionIssuer(SessionRepository sessions, OneTimeTokenRepository tokens, TokenPort tokenPort, Clock clock, AuthSettings settings) {
    this.sessions = sessions;
    this.tokens = tokens;
    this.tokenPort = tokenPort;
    this.clock = clock;
    this.settings = settings;
  }

  public LoginUseCase.Result issue(UUID userId) {
    Instant now = clock.instant();
    String token = tokenPort.newToken();
    Instant expires = now.plus(settings.sessionTtl());
    sessions.insert(new Session(UUID.randomUUID(), userId, tokenPort.hash(token), now, expires));
    return new LoginUseCase.Result(token, expires);
  }

  /** Après un premier facteur prouvé : session directe, ou jeton de second facteur si le TOTP est actif. */
  public AuthOutcome afterFirstFactor(User user) {
    if (user.totpEnabled()) {
      String token = tokenPort.newToken();
      tokens.insert(user.id(), Purpose.MFA_PENDING, tokenPort.hash(token), clock.instant().plus(settings.mfaTtl()));
      return new AuthOutcome(null, null, token);
    }
    LoginUseCase.Result session = issue(user.id());
    return new AuthOutcome(session.sessionToken(), session.expiresAt(), null);
  }
}
