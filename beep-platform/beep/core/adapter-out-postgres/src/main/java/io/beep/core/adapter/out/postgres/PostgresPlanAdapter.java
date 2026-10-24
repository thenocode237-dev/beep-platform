package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.PlanRepository;
import io.beep.core.domain.plan.Plan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresPlanAdapter implements PlanRepository {
  private final DSLContext dsl;

  public PostgresPlanAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Plan> findByCode(String code) {
    return dsl.selectFrom(PLANS).where(P_CODE.eq(code)).fetchOptional(PostgresPlanAdapter::map);
  }

  @Override
  public List<Plan> findAll() {
    return dsl.selectFrom(PLANS).orderBy(P_PRICE.asc(), P_CODE.asc()).fetch(PostgresPlanAdapter::map);
  }

  @Override
  public List<EntitlementOverride> overrides(UUID workspaceId) {
    return dsl.selectFrom(OVERRIDES).where(O_WORKSPACE.eq(workspaceId)).orderBy(O_KEY)
        .fetch(r -> new EntitlementOverride(r.get(O_KEY), r.get(O_VALUE), r.get(O_REASON), instant(r.get(O_AT)), r.get(O_BY)));
  }

  @Override
  public void applyOverrides(UUID workspaceId, Map<String, Long> changes, String reason, String staffId, Instant at) {
    changes.forEach((key, value) -> {
      if (value == null) {
        dsl.deleteFrom(OVERRIDES).where(O_WORKSPACE.eq(workspaceId).and(O_KEY.eq(key))).execute();
      } else {
        dsl.execute("INSERT INTO entitlement_overrides (workspace_id, key, value, reason, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?::timestamptz) "
                + "ON CONFLICT (workspace_id, key) DO UPDATE SET value = EXCLUDED.value, reason = EXCLUDED.reason, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at",
            workspaceId, key, value, reason, staffId, odt(at));
      }
    });
  }

  private static Plan map(Record r) {
    return new Plan(r.get(P_CODE), r.get(P_NAME), r.get(P_VERSION), r.get(P_PRICE), r.get(P_PUBLIC), Sql.longs(r.get(P_ENTITLEMENTS)));
  }
}
