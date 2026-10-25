package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.instant;
import static io.beep.core.adapter.out.postgres.Schema.odt;

import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.PaymentMethod;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresInvoiceAdapter implements InvoiceRepository {
  private static final String COLUMNS = "i.number, i.payment_id, i.workspace_id, i.issued_at, i.customer_name, i.customer_email, i.country, i.plan_name, i.billing_cycle, "
      + "i.period_start, i.period_end, i.list_price_xaf, i.discount_xaf, i.credit_xaf, i.gross_xaf, i.net_xaf, i.vat_xaf, i.vat_rate_bp, i.method";

  private final DSLContext dsl;

  public PostgresInvoiceAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Le compteur de l'année est incrémenté par un UPDATE … RETURNING (ligne verrouillée jusqu'au COMMIT) : deux paiements simultanés
   * sont sérialisés et, si la transaction échoue, le numéro n'est pas consommé — séquence continue, sans trou.
   */
  @Override
  public Invoice issue(Invoice d) {
    Optional<Invoice> existing = findByPayment(d.paymentId());
    if (existing.isPresent()) return existing.get();
    int year = d.issuedAt().atZone(ZoneOffset.UTC).getYear();
    dsl.execute("INSERT INTO invoice_counters (year, last_value) VALUES (?, 0) ON CONFLICT (year) DO NOTHING", year);
    long seq = dsl.fetchOne("UPDATE invoice_counters SET last_value = last_value + 1 WHERE year = ? RETURNING last_value", year).get(0, Long.class);
    String number = Invoice.format(year, seq);
    dsl.execute("INSERT INTO invoices (number, payment_id, workspace_id, issued_at, customer_name, customer_email, country, plan_name, billing_cycle, period_start, period_end, "
            + "list_price_xaf, discount_xaf, credit_xaf, gross_xaf, net_xaf, vat_xaf, vat_rate_bp, method) "
            + "VALUES (?, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?)",
        number, d.paymentId(), d.workspaceId(), odt(d.issuedAt()), d.customerName(), d.customerEmail(), d.country(), d.planName(), d.cycle().db(), odt(d.periodStart()),
        odt(d.periodEnd()), d.listPriceXaf(), d.discountXaf(), d.creditXaf(), d.grossXaf(), d.netXaf(), d.vatXaf(), d.vatRateBp(), d.method().db());
    return findByNumber(number).orElseThrow();
  }

  @Override
  public Optional<Invoice> findByNumber(String number) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM invoices i WHERE i.number = ?", number).map(PostgresInvoiceAdapter::map);
  }

  @Override
  public Optional<Invoice> findByPayment(UUID paymentId) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM invoices i WHERE i.payment_id = ?", paymentId).map(PostgresInvoiceAdapter::map);
  }

  @Override
  public List<Invoice> forWorkspace(UUID workspaceId, int limit) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM invoices i WHERE i.workspace_id = ? ORDER BY i.issued_at DESC, i.number DESC LIMIT ?", workspaceId, limit)
        .map(PostgresInvoiceAdapter::map);
  }

  private static Invoice map(Record r) {
    return new Invoice(r.get("number", String.class), r.get("payment_id", UUID.class), r.get("workspace_id", UUID.class), instant(r.get("issued_at", OffsetDateTime.class)),
        r.get("customer_name", String.class), r.get("customer_email", String.class), r.get("country", String.class), r.get("plan_name", String.class),
        BillingCycle.valueOf(r.get("billing_cycle", String.class).toUpperCase(Locale.ROOT)), instant(r.get("period_start", OffsetDateTime.class)),
        instant(r.get("period_end", OffsetDateTime.class)), r.get("list_price_xaf", Long.class), r.get("discount_xaf", Long.class), r.get("credit_xaf", Long.class),
        r.get("gross_xaf", Long.class), r.get("net_xaf", Long.class), r.get("vat_xaf", Long.class), r.get("vat_rate_bp", Integer.class),
        PaymentMethod.valueOf(r.get("method", String.class).toUpperCase(Locale.ROOT)));
  }
}
