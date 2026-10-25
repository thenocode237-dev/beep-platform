package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.instant;
import static io.beep.core.adapter.out.postgres.Schema.odt;

import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresSubscriptionAdapter implements SubscriptionRepository {
  private static final String COLUMNS = "s.id, s.workspace_id, s.plan_code, s.billing_cycle, s.status, s.period_start, s.period_end, s.monthly_rate_xaf, s.notices, "
      + "s.trial_used, s.created_at, s.updated_at";

  private final DSLContext dsl;

  public PostgresSubscriptionAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Subscription> findByWorkspace(UUID workspaceId) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM subscriptions s WHERE s.workspace_id = ?", workspaceId).map(PostgresSubscriptionAdapter::map);
  }

  @Override
  public Optional<Subscription> lockByWorkspace(UUID workspaceId) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM subscriptions s WHERE s.workspace_id = ? FOR UPDATE", workspaceId).map(PostgresSubscriptionAdapter::map);
  }

  @Override
  public void save(Subscription s) {
    dsl.execute("INSERT INTO subscriptions (id, workspace_id, plan_code, billing_cycle, status, period_start, period_end, monthly_rate_xaf, notices, trial_used, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?::text[], ?, ?::timestamptz, ?::timestamptz) "
            + "ON CONFLICT (workspace_id) DO UPDATE SET plan_code = EXCLUDED.plan_code, billing_cycle = EXCLUDED.billing_cycle, status = EXCLUDED.status, "
            + "period_start = EXCLUDED.period_start, period_end = EXCLUDED.period_end, monthly_rate_xaf = EXCLUDED.monthly_rate_xaf, notices = EXCLUDED.notices, "
            + "trial_used = EXCLUDED.trial_used, updated_at = EXCLUDED.updated_at",
        s.id(), s.workspaceId(), s.planCode(), s.cycle() == null ? null : s.cycle().db(), s.status().db(), odt(s.periodStart()), odt(s.periodEnd()), s.monthlyRateXaf(),
        s.notices().toArray(new String[0]), s.trialUsed(), odt(s.createdAt()), odt(s.updatedAt()));
  }

  /** Non expirés dont l'échéance approche, et expirés dont le message de fin n'est pas encore parti. */
  @Override
  public List<Subscription> candidates(Instant horizon, int limit) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM subscriptions s WHERE (s.status <> 'expired' AND s.period_end <= ?::timestamptz) "
            + "OR (s.status = 'expired' AND s.period_end > s.created_at AND NOT ('expired' = ANY (s.notices))) ORDER BY s.period_end LIMIT ?", odt(horizon), limit)
        .map(PostgresSubscriptionAdapter::map);
  }

  @Override
  public List<Row> page(SubscriptionStatus status, int limit, int offset) {
    String where = status == null ? "" : "WHERE s.status = '" + status.db() + "' ";
    return dsl.fetch("SELECT " + COLUMNS + ", w.name AS ws_name, u.email AS owner_email FROM subscriptions s JOIN workspaces w ON w.id = s.workspace_id "
            + "JOIN users u ON u.id = w.owner_id " + where + "ORDER BY s.period_end DESC, s.id LIMIT ? OFFSET ?", limit, offset)
        .map(r -> new Row(map(r), r.get("ws_name", String.class), r.get("owner_email", String.class)));
  }

  @Override
  public Summary summary() {
    Record r = dsl.fetchOne("SELECT count(*) FILTER (WHERE status = 'trialing') AS trialing, count(*) FILTER (WHERE status = 'active') AS active, "
        + "count(*) FILTER (WHERE status = 'grace') AS grace, count(*) FILTER (WHERE status = 'expired') AS expired, "
        + "COALESCE(sum(monthly_rate_xaf) FILTER (WHERE status = 'active'), 0) AS mrr FROM subscriptions");
    return new Summary(r.get("trialing", Long.class), r.get("active", Long.class), r.get("grace", Long.class), r.get("expired", Long.class), r.get("mrr", Long.class));
  }

  @Override
  public List<Row> upcoming(Instant now, Instant until, int limit) {
    return dsl.fetch("SELECT " + COLUMNS + ", w.name AS ws_name, u.email AS owner_email FROM subscriptions s JOIN workspaces w ON w.id = s.workspace_id "
            + "JOIN users u ON u.id = w.owner_id WHERE s.status IN ('trialing', 'active') AND s.period_end > ?::timestamptz AND s.period_end <= ?::timestamptz "
            + "ORDER BY s.period_end, s.id LIMIT ?", odt(now), odt(until), limit)
        .map(r -> new Row(map(r), r.get("ws_name", String.class), r.get("owner_email", String.class)));
  }

  private static Subscription map(Record r) {
    String[] notices = r.get("notices", String[].class);
    String cycle = r.get("billing_cycle", String.class);
    return new Subscription(r.get("id", UUID.class), r.get("workspace_id", UUID.class), r.get("plan_code", String.class),
        cycle == null ? null : BillingCycle.valueOf(cycle.toUpperCase(java.util.Locale.ROOT)), SubscriptionStatus.parse(r.get("status", String.class)),
        instant(r.get("period_start", java.time.OffsetDateTime.class)), instant(r.get("period_end", java.time.OffsetDateTime.class)), r.get("monthly_rate_xaf", Long.class),
        notices == null ? Set.of() : Set.copyOf(Arrays.asList(notices)), r.get("trial_used", Boolean.class), instant(r.get("created_at", java.time.OffsetDateTime.class)),
        instant(r.get("updated_at", java.time.OffsetDateTime.class)));
  }
}
