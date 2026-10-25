package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.instant;
import static io.beep.core.adapter.out.postgres.Schema.odt;

import io.beep.core.app.port.PaymentRepository;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentMethod;
import io.beep.core.domain.billing.PaymentStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresPaymentAdapter implements PaymentRepository {
  private static final String COLUMNS = "p.id, p.workspace_id, p.plan_code, p.billing_cycle, p.method, p.provider, p.provider_ref, p.status, p.list_price_xaf, p.discount_xaf, "
      + "p.credit_xaf, p.amount_xaf, p.coupon_code, p.country, p.phone_hint, p.note, p.recorded_by, p.failure_reason, p.created_at, p.paid_at";

  private final DSLContext dsl;

  public PostgresPaymentAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Payment p) {
    try {
      dsl.execute("INSERT INTO payments (id, workspace_id, plan_code, billing_cycle, method, provider, provider_ref, status, list_price_xaf, discount_xaf, credit_xaf, amount_xaf, "
              + "coupon_code, country, phone_hint, note, recorded_by, failure_reason, created_at, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)",
          p.id(), p.workspaceId(), p.planCode(), p.cycle().db(), p.method().db(), p.provider(), p.providerRef(), p.status().db(), p.listPriceXaf(), p.discountXaf(), p.creditXaf(),
          p.amountXaf(), p.couponCode(), p.country(), p.phoneHint(), p.note(), p.recordedBy(), p.failureReason(), odt(p.createdAt()), odt(p.paidAt()));
    } catch (org.jooq.exception.DataAccessException e) {
      if (Sql.hasState(e, Sql.UNIQUE_VIOLATION)) throw new DuplicateReferenceException(e);
      throw e;
    }
  }

  @Override
  public Optional<Payment> findById(UUID id) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM payments p WHERE p.id = ?", id).map(PostgresPaymentAdapter::map);
  }

  @Override
  public Optional<Payment> findByProviderRef(String provider, String providerRef) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM payments p WHERE p.provider = ? AND p.provider_ref = ?", provider, providerRef).map(PostgresPaymentAdapter::map);
  }

  @Override
  public Optional<Payment> lockById(UUID id) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM payments p WHERE p.id = ? FOR UPDATE", id).map(PostgresPaymentAdapter::map);
  }

  @Override
  public void update(Payment p) {
    dsl.execute("UPDATE payments SET provider_ref = ?, status = ?, failure_reason = ?, paid_at = ?::timestamptz WHERE id = ?",
        p.providerRef(), p.status().db(), p.failureReason(), odt(p.paidAt()), p.id());
  }

  @Override
  public List<Payment> forWorkspace(UUID workspaceId, int limit) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM payments p WHERE p.workspace_id = ? ORDER BY p.created_at DESC, p.id DESC LIMIT ?", workspaceId, limit)
        .map(PostgresPaymentAdapter::map);
  }

  @Override
  public List<Payment> pendingBefore(Instant before, int limit) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM payments p WHERE p.status = 'pending' AND p.created_at < ?::timestamptz ORDER BY p.created_at LIMIT ?", odt(before), limit)
        .map(PostgresPaymentAdapter::map);
  }

  @Override
  public Page page(PaymentStatus status, UUID workspaceId, int limit, String cursor) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE true");
    if (status != null) {
      where.append(" AND p.status = ?");
      args.add(status.db());
    }
    if (workspaceId != null) {
      where.append(" AND p.workspace_id = ?");
      args.add(workspaceId);
    }
    if (cursor != null && !cursor.isBlank()) {
      Sql.Cursor c = Sql.cursor(cursor);
      where.append(" AND (p.created_at, p.id) < (?::timestamptz, ?)");
      args.add(odt(c.at()));
      args.add(c.id());
    }
    args.add(limit + 1);
    List<Row> rows = dsl.fetch("SELECT " + COLUMNS + ", w.name AS ws_name, u.email AS owner_email FROM payments p JOIN workspaces w ON w.id = p.workspace_id "
            + "JOIN users u ON u.id = w.owner_id " + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT ?", args.toArray())
        .map(r -> new Row(map(r), r.get("ws_name", String.class), r.get("owner_email", String.class)));
    if (rows.size() <= limit) return new Page(rows, null);
    List<Row> items = rows.subList(0, limit);
    Payment last = items.get(limit - 1).payment();
    return new Page(items, Sql.cursor(last.createdAt(), last.id()));
  }

  @Override
  public long succeededSince(Instant since) {
    return dsl.fetchOne("SELECT COALESCE(sum(amount_xaf), 0) FROM payments WHERE status = 'succeeded' AND paid_at >= ?::timestamptz", odt(since)).get(0, Long.class);
  }

  @Override
  public long countByStatus(PaymentStatus status) {
    return dsl.fetchOne("SELECT count(*) FROM payments WHERE status = ?", status.db()).get(0, Long.class);
  }

  private static Payment map(Record r) {
    return new Payment(r.get("id", UUID.class), r.get("workspace_id", UUID.class), r.get("plan_code", String.class),
        BillingCycle.valueOf(r.get("billing_cycle", String.class).toUpperCase(Locale.ROOT)), PaymentMethod.valueOf(r.get("method", String.class).toUpperCase(Locale.ROOT)),
        r.get("provider", String.class), r.get("provider_ref", String.class), PaymentStatus.parse(r.get("status", String.class)), r.get("list_price_xaf", Long.class),
        r.get("discount_xaf", Long.class), r.get("credit_xaf", Long.class), r.get("amount_xaf", Long.class), r.get("coupon_code", String.class), r.get("country", String.class),
        r.get("phone_hint", String.class), r.get("note", String.class), r.get("recorded_by", String.class), r.get("failure_reason", String.class),
        instant(r.get("created_at", OffsetDateTime.class)), instant(r.get("paid_at", OffsetDateTime.class)));
  }
}
