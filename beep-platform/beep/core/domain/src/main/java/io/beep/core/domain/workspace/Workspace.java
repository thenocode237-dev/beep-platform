package io.beep.core.domain.workspace;

import io.beep.core.domain.DomainException;
import java.time.Instant;
import java.util.UUID;

/**
 * Conteneur des liens, QR codes et pages bio, porteur du plan. Phase 1 : un workspace personnel par utilisateur
 * (son propriétaire) ; les équipes arrivent en phase 4.
 */
public record Workspace(UUID id, UUID ownerId, String name, String planCode, WorkspaceStatus status, Instant firstLinkAt, Instant createdAt) {
  public static final String DEFAULT_PLAN = "starter";

  public static Workspace personal(UUID id, UUID ownerId, String name, Instant now) {
    return new Workspace(id, ownerId, name, DEFAULT_PLAN, WorkspaceStatus.ACTIVE, null, now);
  }

  public boolean suspended() {
    return status == WorkspaceStatus.SUSPENDED;
  }

  /** Toute écriture (création, modification) exige un workspace actif. */
  public void requireWritable() {
    if (suspended()) throw new DomainException("workspace_suspended");
  }

  public Workspace withStatus(WorkspaceStatus next) {
    return new Workspace(id, ownerId, name, planCode, next, firstLinkAt, createdAt);
  }
}
