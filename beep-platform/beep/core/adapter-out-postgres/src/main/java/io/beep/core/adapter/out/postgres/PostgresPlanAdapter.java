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

  @Override
  public void publish(Plan plan, String staffId, Instant at) {
    dsl.execute("INSERT INTO plans (code, name, version, price_xaf, price_quarterly_xaf, price_annual_xaf, public, entitlements, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::timestamptz) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, version = EXCLUDED.version, price_xaf = EXCLUDED.price_xaf, price_quarterly_xaf = EXCLUDED.price_quarterly_xaf, "
            + "price_annual_xaf = EXCLUDED.price_annual_xaf, public = EXCLUDED.public, entitlements = EXCLUDED.entitlements, updated_at = EXCLUDED.updated_at",
        plan.code(), plan.name(), plan.version(), plan.priceXaf(), plan.priceQuarterlyXaf(), plan.priceAnnualXaf(), plan.publicPlan(), Sql.json(new java.util.TreeMap<>(plan.entitlements())).data(),
        odt(at));
    dsl.execute("INSERT INTO plan_versions (code, version, snapshot, published_by, published_at) VALUES (?, ?, ?::jsonb, ?, ?::timestamptz)",
        plan.code(), plan.version(), Sql.json(java.util.Map.of("name", plan.name(), "price_xaf", plan.priceXaf(), "price_quarterly_xaf", plan.priceQuarterlyXaf(), "price_annual_xaf",
            plan.priceAnnualXaf(), "public", plan.publicPlan(), "entitlements", new java.util.TreeMap<>(plan.entitlements()))).data(), staffId, odt(at));
  }

  private static Plan map(Record r) {
    long monthly = r.get(P_PRICE);
    Long quarterly = r.get(P_PRICE_Q);
    Long annual = r.get(P_PRICE_A);
    return new Plan(r.get(P_CODE), r.get(P_NAME), r.get(P_VERSION), monthly, quarterly != null ? quarterly : io.beep.core.domain.billing.Pricing.quarterly(monthly),
        annual != null ? annual : io.beep.core.domain.billing.Pricing.annual(monthly), r.get(P_PUBLIC), Sql.longs(r.get(P_ENTITLEMENTS)));
  }
}
