package io.beep.core.app.admin;

import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Entitlements;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.UserStatus;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fiche utilisateur et suspension/réactivation de compte (API admin). */
public class AdminUsersUseCase {
  public record Detail(User user, Workspace workspace, List<QuotaPolicy.Status> usage, Map<String, Long> entitlements, List<PlanRepository.EntitlementOverride> overrides) {}

  private final UserRepository users;
  private final WorkspaceRepository workspaceRepository;
  private final Workspaces workspaces;
  private final PlanRepository plans;
  private final SessionRepository sessions;
  private final TransactionPort tx;

  public AdminUsersUseCase(UserRepository users, WorkspaceRepository workspaceRepository, Workspaces workspaces, PlanRepository plans,
      SessionRepository sessions, TransactionPort tx) {
    this.users = users;
    this.workspaceRepository = workspaceRepository;
    this.workspaces = workspaces;
    this.plans = plans;
    this.sessions = sessions;
    this.tx = tx;
  }

  public Detail detail(UUID userId) {
    User user = users.findById(userId).orElseThrow(() -> new DomainException("not_found"));
    Optional<Workspace> workspace = workspaceRepository.findByOwner(user.id());
    if (workspace.isEmpty()) return new Detail(user, null, List.of(), Map.of(), List.of()); // jamais connecté depuis la phase 1
    Workspace w = workspace.get();
    Entitlements e = workspaces.entitlements(w);
    return new Detail(user, w, workspaces.quotas(w.id(), e), e.values(), plans.overrides(w.id()));
  }

  /**
   * Suspension : connexion impossible, sessions révoquées, workspace suspendu (liens et pages ne redirigent plus).
   * Réactivation : retour à « actif » (ou « en attente » si l'adresse n'a jamais été confirmée).
   */
  public User setStatus(UUID userId, String status, String reason) {
    Link.reason(reason); // motif obligatoire : il est journalisé par le back office
    boolean suspend = switch (status == null ? "" : status) {
      case "suspended" -> true;
      case "active" -> false;
      default -> throw new DomainException("invalid_request");
    };
    User user = users.findById(userId).orElseThrow(() -> new DomainException("not_found"));
    if (suspend == (user.status() == UserStatus.SUSPENDED)) throw new DomainException("status_unchanged");
    User updated = user.withStatus(suspend ? UserStatus.SUSPENDED : user.emailVerified() ? UserStatus.ACTIVE : UserStatus.PENDING);
    tx.run(() -> {
      users.update(updated);
      if (suspend) sessions.deleteAllForUser(user.id());
      workspaceRepository.findByOwner(user.id()).ifPresent(w -> workspaceRepository.updateStatus(w.id(), suspend ? WorkspaceStatus.SUSPENDED : WorkspaceStatus.ACTIVE));
    });
    return updated;
  }
}
