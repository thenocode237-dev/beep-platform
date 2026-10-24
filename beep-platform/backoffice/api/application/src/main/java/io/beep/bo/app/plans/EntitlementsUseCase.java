package io.beep.bo.app.plans;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Module « Plans & droits » (phase 1 : lecture des plans + overrides par workspace). L'édition et le versionnement des plans,
 * avec publication vers le core, arrivent en phase 2.
 */
public class EntitlementsUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public EntitlementsUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public List<CoreGatewayPort.PlanView> plans(StaffUser actor) {
    actor.require(Permission.PLANS_READ);
    return core.plans(actor.id());
  }

  /** Valeur null = suppression de l'override. Motif obligatoire, ré-authentification TOTP, avant/après journalisés. */
  public CoreGatewayPort.WorkspaceEntitlements setOverrides(StaffUser actor, UUID workspaceId, Map<String, Long> overrides, String reason, String totp, String ip) {
    actor.require(Permission.ENTITLEMENTS_WRITE);
    if (overrides == null || overrides.isEmpty()) throw new DomainException("invalid_request");
    if (reason == null || reason.strip().length() < 3) throw new DomainException("invalid_reason");
    stepUp.require(actor, totp);
    CoreGatewayPort.WorkspaceEntitlements result = core.setEntitlements(workspaceId, overrides, reason.strip(), actor.id());
    String changes = new TreeMap<>(overrides).entrySet().stream().map(e -> e.getKey() + "=" + (e.getValue() == null ? "plan" : e.getValue()))
        .collect(Collectors.joining(","));
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "entitlements.override", "workspace:" + workspaceId,
        "changements=" + changes + " motif=" + reason.strip(), ip));
    return result;
  }
}
