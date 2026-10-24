package io.beep.core.app.port;

import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {
  Optional<Workspace> findById(UUID id);

  Optional<Workspace> findByOwner(UUID ownerId);

  /** Crée le workspace personnel s'il n'existe pas encore ; retourne celui qui fait foi (idempotent, sûr en concurrence). */
  Workspace ensure(Workspace candidate);

  /** Verrouille la ligne jusqu'à la fin de la transaction : sérialise les créations d'un workspace (contrôle de quota exact). */
  void lock(UUID id);

  void updateStatus(UUID id, WorkspaceStatus status);

  /** Mémorise la date du premier lien (mesure de l'activation) ; sans effet si elle est déjà connue. */
  void markFirstLink(UUID id, Instant at);
}
