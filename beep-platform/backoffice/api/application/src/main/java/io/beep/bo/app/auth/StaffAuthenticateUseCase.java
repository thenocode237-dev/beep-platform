package io.beep.bo.app.auth;

import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.app.port.StaffSessionRepository;
import io.beep.bo.app.port.TokenPort;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Résout un jeton de session en membre du staff et prolonge l'activité (expiration sur inactivité). */
public class StaffAuthenticateUseCase {
  private final StaffSessionRepository sessions;
  private final StaffRepository staff;
  private final TokenPort tokenPort;
  private final Clock clock;
  private final StaffSettings settings;

  public StaffAuthenticateUseCase(StaffSessionRepository sessions, StaffRepository staff, TokenPort tokenPort, Clock clock, StaffSettings settings) {
    this.sessions = sessions;
    this.staff = staff;
    this.tokenPort = tokenPort;
    this.clock = clock;
    this.settings = settings;
  }

  public Optional<StaffUser> execute(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) return Optional.empty();
    Instant now = clock.instant();
    String hash = tokenPort.hash(sessionToken);
    return sessions.findActive(hash, now, now.minus(settings.idleTimeout()))
        .flatMap(s -> staff.findById(s.staffId()))
        .filter(StaffUser::canLogin)
        .map(u -> {
          sessions.touch(hash, now);
          return u;
        });
  }
}
