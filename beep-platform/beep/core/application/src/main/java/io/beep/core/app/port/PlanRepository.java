package io.beep.core.app.port;

import io.beep.core.domain.plan.Plan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Plans publiés et overrides de droits par workspace. */
public interface PlanRepository {
  Optional<Plan> findByCode(String code);

  List<Plan> findAll();

  List<EntitlementOverride> overrides(UUID workspaceId);

  /** Applique les changements : une valeur remplace l'override, `null` le supprime. */
  void applyOverrides(UUID workspaceId, Map<String, Long> changes, String reason, String staffId, Instant at);

  record EntitlementOverride(String key, long value, String reason, Instant updatedAt, String updatedBy) {}
}
