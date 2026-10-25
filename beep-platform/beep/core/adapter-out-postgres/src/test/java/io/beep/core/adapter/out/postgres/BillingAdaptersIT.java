package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.port.PaymentRepository;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Coupon;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentMethod;
import io.beep.core.domain.billing.PaymentStatus;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Adapters de facturation (phase 2) sur un PostgreSQL réel : abonnements, paiements, factures sans trou, coupons, plans versionnés. */
class BillingAdaptersIT {
  static final Instant NOW = Instant.parse("2026-03-10T10:00:00Z");
  static DSLContext dsl;

  @BeforeAll
  static void start() {
    PGSimpleDataSource pg = new PGSimpleDataSource();
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || url.isBlank()) {
      PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine");
      c.start();
      url = c.getJdbcUrl();
      pg.setUser(c.getUsername());
      pg.setPassword(c.getPassword());
    }
    pg.setUrl(url);
    Flyway flyway = Flyway.configure().dataSource(pg).cleanDisabled(false).load();
    flyway.clean();
    flyway.migrate();
    dsl = DSL.using(pg, SQLDialect.POSTGRES);
  }

  PostgresSubscriptionAdapter subscriptions;
  PostgresPaymentAdapter payments;
  PostgresInvoiceAdapter invoices;
  PostgresCouponAdapter coupons;
  PostgresPlanAdapter plans;
  Workspace ws;

  @BeforeEach
  void init() {
    dsl.execute("TRUNCATE users, invoice_counters, coupons CASCADE");
    dsl.execute("DELETE FROM plan_versions");
    dsl.execute("UPDATE plans SET price_xaf = 4999, price_quarterly_xaf = NULL, price_annual_xaf = NULL, version = 1, entitlements = (SELECT entitlements FROM plans WHERE code = 'pro') WHERE code = 'pro'");
    subscriptions = new PostgresSubscriptionAdapter(dsl);
    payments = new PostgresPaymentAdapter(dsl);
    invoices = new PostgresInvoiceAdapter(dsl);
    coupons = new PostgresCouponAdapter(dsl);
    plans = new PostgresPlanAdapter(dsl);
    User u = User.register(UUID.randomUUID(), new Email("ada@example.com"), "h", "fr", NOW).verifyEmail(NOW);
    new PostgresUserAdapter(dsl).insert(u);
    ws = new PostgresWorkspaceAdapter(dsl).ensure(Workspace.personal(UUID.randomUUID(), u.id(), "ada", NOW));
  }

  Payment payment(String ref) {
    Payment p = new Payment(UUID.randomUUID(), ws.id(), "pro", BillingCycle.MONTHLY, PaymentMethod.MTN_MOMO, "sandbox", ref, PaymentStatus.PENDING, 4999, 0, 0, 4999, null, "CM",
        "0001", null, null, null, NOW, null);
    payments.insert(p);
    return p;
  }

  @Test
  void migrationOpensProAndSeedsDerivedPrices() {
    Plan pro = plans.findByCode("pro").orElseThrow();
    assertThat(pro.publicPlan()).isTrue();
    assertThat(pro.priceQuarterlyXaf()).isEqualTo(13_500);
    assertThat(pro.priceAnnualXaf()).isEqualTo(41_990);
    assertThat(plans.findByCode("elite").orElseThrow().publicPlan()).isTrue();
  }

  @Test
  void publishingAPlanReplacesItAndKeepsAVersionSnapshot() {
    plans.publish(new Plan("pro", "Pro", 2, 5499, 14_000, 45_000, true, Map.of("links.stock_max", 60L)), "staff-1", NOW);
    Plan pro = plans.findByCode("pro").orElseThrow();
    assertThat(pro.version()).isEqualTo(2);
    assertThat(pro.priceQuarterlyXaf()).isEqualTo(14_000);
    assertThat(pro.entitlements()).containsEntry("links.stock_max", 60L);
    assertThat(dsl.fetchOne("SELECT count(*) FROM plan_versions WHERE code = 'pro'").get(0, Long.class)).isEqualTo(1);
  }

  @Test
  void subscriptionRoundTripsAndUpserts() {
    Subscription s = Subscription.none(ws.id(), NOW).extend("pro", BillingCycle.QUARTERLY, 13_500, NOW).withNotice("d7", NOW);
    subscriptions.save(s);
    Subscription read = subscriptions.findByWorkspace(ws.id()).orElseThrow();
    assertThat(read.planCode()).isEqualTo("pro");
    assertThat(read.cycle()).isEqualTo(BillingCycle.QUARTERLY);
    assertThat(read.periodEnd()).isEqualTo(Instant.parse("2026-06-10T10:00:00Z"));
    assertThat(read.monthlyRateXaf()).isEqualTo(4_500);
    assertThat(read.notices()).containsExactly("d7");

    subscriptions.save(read.withStatus(SubscriptionStatus.GRACE, NOW));
    assertThat(subscriptions.findByWorkspace(ws.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.GRACE);
    assertThat(dsl.fetchOne("SELECT count(*) FROM subscriptions").get(0, Long.class)).isEqualTo(1);

    Subscription trial = Subscription.none(ws.id(), NOW).startTrial(NOW);
    subscriptions.save(trial);
    assertThat(subscriptions.findByWorkspace(ws.id()).orElseThrow().cycle()).isNull();
    assertThat(subscriptions.lockByWorkspace(ws.id())).isPresent();
  }

  @Test
  void candidatesSummaryAndUpcoming() {
    subscriptions.save(Subscription.none(ws.id(), NOW).extend("pro", BillingCycle.MONTHLY, 4_999, NOW));
    assertThat(subscriptions.candidates(NOW.plus(Duration.ofDays(40)), 10)).hasSize(1);
    assertThat(subscriptions.candidates(NOW.plus(Duration.ofDays(5)), 10)).isEmpty();
    var summary = subscriptions.summary();
    assertThat(summary.active()).isEqualTo(1);
    assertThat(summary.mrrXaf()).isEqualTo(4_999);
    assertThat(subscriptions.upcoming(NOW, NOW.plus(Duration.ofDays(40)), 10)).singleElement().satisfies(r -> assertThat(r.ownerEmail()).isEqualTo("ada@example.com"));
    assertThat(subscriptions.page(SubscriptionStatus.ACTIVE, 10, 0)).hasSize(1);
  }

  @Test
  void paymentsAreUniquePerProviderReferenceAndPaged() {
    Payment p = payment("ref-1");
    assertThatThrownBy(() -> payment("ref-1")).isInstanceOf(PaymentRepository.DuplicateReferenceException.class);
    assertThat(payments.findByProviderRef("sandbox", "ref-1")).get().extracting(Payment::id).isEqualTo(p.id());
    assertThat(payments.pendingBefore(NOW.plusSeconds(1), 10)).hasSize(1);

    payments.update(p.succeeded(NOW.plusSeconds(5)));
    assertThat(payments.findById(p.id()).orElseThrow().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payments.succeededSince(NOW)).isEqualTo(4_999);
    assertThat(payments.countByStatus(PaymentStatus.PENDING)).isZero();

    for (int i = 0; i < 3; i++) payment("more-" + i);
    var first = payments.page(null, null, 2, null);
    assertThat(first.items()).hasSize(2);
    assertThat(first.nextCursor()).isNotNull();
    assertThat(payments.page(null, null, 2, first.nextCursor()).items()).hasSize(2);
    assertThat(payments.page(PaymentStatus.SUCCEEDED, ws.id(), 10, null).items()).hasSize(1);
  }

  @Test
  void invoiceNumbersAreSequentialPerYearAndIdempotentPerPayment() {
    Payment a = payment("a");
    Payment b = payment("b");
    Invoice first = invoices.issue(draft(a, NOW));
    Invoice again = invoices.issue(draft(a, NOW));
    Invoice second = invoices.issue(draft(b, NOW));
    Invoice nextYear = invoices.issue(draft(payment("c"), Instant.parse("2027-01-05T00:00:00Z")));

    assertThat(first.number()).isEqualTo("BEEP-2026-000001");
    assertThat(again.number()).isEqualTo(first.number());
    assertThat(second.number()).isEqualTo("BEEP-2026-000002");
    assertThat(nextYear.number()).isEqualTo("BEEP-2027-000001");
    assertThat(invoices.findByNumber(first.number())).isPresent();
    assertThat(invoices.forWorkspace(ws.id(), 10)).hasSize(3);
    assertThat(first.grossXaf()).isEqualTo(first.netXaf() + first.vatXaf());
  }

  @Test
  void aRolledBackInvoiceDoesNotConsumeANumber() {
    Payment a = payment("a");
    org.jooq.impl.DSL.using(dsl.configuration()).transaction(cfg -> {
      var tx = new PostgresInvoiceAdapter(DSL.using(cfg));
      tx.issue(draft(a, NOW));
      cfg.dsl().execute("SELECT 1");
    });
    Payment b = payment("b");
    try {
      dsl.transaction(cfg -> {
        new PostgresInvoiceAdapter(cfg.dsl()).issue(draft(b, NOW));
        throw new IllegalStateException("rollback");
      });
    } catch (RuntimeException expected) {
      // la transaction est annulée
    }
    Payment c = payment("c");
    assertThat(invoices.issue(draft(c, NOW)).number()).isEqualTo("BEEP-2026-000002");
  }

  @Test
  void couponsRoundTripAndRejectDuplicates() {
    Coupon c = Coupon.create("welcome20", 20, 0, 5, NOW.plus(Duration.ofDays(30)), Set.of("pro"), NOW);
    coupons.insert(c);
    assertThatThrownBy(() -> coupons.insert(c)).isInstanceOf(io.beep.core.app.port.CouponRepository.DuplicateCodeException.class);
    Coupon read = coupons.find("WELCOME20").orElseThrow();
    assertThat(read.planCodes()).containsExactly("pro");
    assertThat(read.maxRedemptions()).isEqualTo(5);
    coupons.update(new Coupon(read.code(), read.percentOff(), read.amountOffXaf(), read.maxRedemptions(), 1, read.validUntil(), read.planCodes(), false, read.createdAt()));
    assertThat(coupons.lock("WELCOME20").orElseThrow().redeemed()).isEqualTo(1);
    assertThat(coupons.list(10)).hasSize(1);
  }

  Invoice draft(Payment p, Instant at) {
    return new Invoice("", p.id(), ws.id(), at, "ada", "ada@example.com", "CM", "Pro", BillingCycle.MONTHLY, at, at.plus(Duration.ofDays(30)), 4_999, 0, 0, 4_999, 4_192, 807,
        1925, PaymentMethod.MTN_MOMO);
  }
}
