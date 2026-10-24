package io.beep.bo.app.users;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.UUID;

/** Fiche utilisateur/workspace (usage face aux quotas) et suspension de compte. Chaque consultation et décision est journalisée. */
public class UserAdminUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public UserAdminUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public CoreGatewayPort.UserDetail detail(StaffUser actor, UUID userId, String ip) {
    actor.require(Permission.USERS_READ);
    CoreGatewayPort.UserDetail d = core.getUser(userId, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "users.view", "user:" + userId, null, ip));
    return d;
  }

  /** Suspension ou réactivation : droit dédié, motif obligatoire, code TOTP de ré-authentification. */
  public CoreGatewayPort.UserView setStatus(StaffUser actor, UUID userId, String status, String reason, String totp, String ip) {
    actor.require(Permission.USERS_SUSPEND);
    if (!"suspended".equals(status) && !"active".equals(status)) throw new DomainException("invalid_request");
    if (reason == null || reason.strip().length() < 3) throw new DomainException("invalid_reason");
    stepUp.require(actor, totp);
    CoreGatewayPort.UserView user = core.setUserStatus(userId, status, reason.strip(), actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "suspended".equals(status) ? "users.suspend" : "users.reactivate",
        "user:" + userId, "email=" + user.email() + " motif=" + reason.strip(), ip));
    return user;
  }
}
