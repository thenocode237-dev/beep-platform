package io.beep.core.app.admin;

import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Entitlements;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Module « Plans & droits » : lecture des plans publiés, overrides par workspace (ex. +50 liens offerts). */
public class AdminEntitlementsUseCase {
  public record Result(Map<String, Long> entitlements, List<PlanRepository.EntitlementOverride> overrides) {}

  private final PlanRepository plans;
  private final WorkspaceRepository workspaceRepository;
  private final Workspaces workspaces;
  private final TransactionPort tx;
  private final Clock clock;

  public AdminEntitlementsUseCase(PlanRepository plans, WorkspaceRepository workspaceRepository, Workspaces workspaces, TransactionPort tx, Clock clock) {
    this.plans = plans;
    this.workspaceRepository = workspaceRepository;
    this.workspaces = workspaces;
    this.tx = tx;
    this.clock = clock;
  }

  public List<Plan> plans() {
    return plans.findAll();
  }

  public Result setOverrides(UUID workspaceId, Map<String, Long> changes, String reason, String staffId) {
    String why = Link.reason(reason);
    if (changes == null || changes.isEmpty()) throw new DomainException("invalid_request");
    changes.forEach(Entitlements::validateOverride);
    Workspace w = workspaceRepository.findById(workspaceId).orElseThrow(() -> new DomainException("not_found"));
    tx.run(() -> plans.applyOverrides(w.id(), changes, why, staffId, clock.instant()));
    return new Result(workspaces.entitlements(w).values(), plans.overrides(w.id()));
  }
}
