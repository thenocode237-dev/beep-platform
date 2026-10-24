package io.beep.bo.app.auth;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.StaffSessionRepository;
import io.beep.bo.app.port.TokenPort;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;

public class StaffLogoutUseCase {
  private final StaffSessionRepository sessions;
  private final TokenPort tokenPort;
  private final AuditPort audit;
  private final Clock clock;

  public StaffLogoutUseCase(StaffSessionRepository sessions, TokenPort tokenPort, AuditPort audit, Clock clock) {
    this.sessions = sessions;
    this.tokenPort = tokenPort;
    this.audit = audit;
    this.clock = clock;
  }

  public void execute(String sessionToken, StaffUser actor, String ip) {
    if (sessionToken != null && !sessionToken.isBlank()) sessions.delete(tokenPort.hash(sessionToken));
    if (actor != null) audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "staff.logout", null, null, ip));
  }
}
