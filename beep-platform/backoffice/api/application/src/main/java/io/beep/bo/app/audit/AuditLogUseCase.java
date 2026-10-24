package io.beep.bo.app.audit;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;

/** Consultation du journal d'audit, avec état d'intégrité de la chaîne. La consultation elle-même est journalisée. */
public class AuditLogUseCase {
  public record View(AuditPort.Page page, long brokenAt, long total) {
    public boolean intact() {
      return brokenAt < 0;
    }
  }

  private final AuditPort audit;
  private final Clock clock;

  public AuditLogUseCase(AuditPort audit, Clock clock) {
    this.audit = audit;
    this.clock = clock;
  }

  public View execute(StaffUser actor, int limit, Long beforeSeq, String action, String actorEmail, String ip) {
    actor.require(Permission.AUDIT_READ);
    var all = audit.readAll();
    long broken = AuditChain.verify(all);
    AuditPort.Page page = audit.page(Math.max(1, Math.min(limit, 100)), beforeSeq, blank(action), blank(actorEmail));
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "audit.read", "audit_log",
        "filtres=" + (blank(action) == null ? "" : "action:" + action.strip()) + (blank(actorEmail) == null ? "" : " auteur:" + actorEmail.strip()), ip));
    return new View(page, broken, all.size());
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
