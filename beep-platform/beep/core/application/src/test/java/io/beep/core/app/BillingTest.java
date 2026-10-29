package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.billing.AdminBillingUseCase;
import io.beep.core.app.billing.BillingEngine;
import io.beep.core.app.billing.BillingLifecycleUseCase;
import io.beep.core.app.billing.BillingOverviewUseCase;
import io.beep.core.app.billing.BillingSettings;
import io.beep.core.app.billing.CheckoutUseCase;
import io.beep.core.app.billing.SettlePaymentUseCase;
import io.beep.core.app.port.CouponRepository;
import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.app.port.PaymentGatewayPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Coupon;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentStatus;
import io.beep.core.domain.billing.Pricing;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import io.beep.core.domain.billing.Vat;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Facturation : devis, paiement Mobile Money, règlement idempotent, factures, prorata, coupons, cycle de vie des échéances. */
class BillingTest {
  static final Map<String, Long> PRO_RIGHTS = Map.of("links.stock_max", 50L, "qr.stock_max", 50L, "bio.max", 3L, "features.export_csv", 1L);
  static final Plan PRO = new Plan("pro", "Pro", 1, 4999, true, PRO_RIGHTS);
  static final Plan ELITE = new Plan("elite", "Elite", 1, 14999, true, Map.of("links.monthly_creations", 500L));

  Platform p;
  Gateway gateway;
  MemSubscriptions subscriptions;
  MemPayments payments;
  MemInvoices invoices;
  MemCoupons coupons;
  BillingEngine engine;
  CheckoutUseCase checkout;
  SettlePaymentUseCase settle;
  BillingOverviewUseCase overview;
  BillingLifecycleUseCase lifecycle;
  AdminBillingUseCase admin;
  User ada;

  @BeforeEach
  void setUp() {
    p = new Platform();
    p.plans.published.put("pro", PRO);
    p.plans.published.put("elite", ELITE);
    gateway = new Gateway();
    subscriptions = new MemSubscriptions();
    payments = new MemPayments();
    invoices = new MemInvoices();
    coupons = new MemCoupons();
    InvoicePdfPort pdf = i -> ("PDF " + i.number()).getBytes();
    engine = new BillingEngine(subscriptions, payments, invoices, coupons, p.plans, p.workspaceRepo, p.users, p.mail, pdf, BillingSettings.defaults());
    checkout = new CheckoutUseCase(p.workspaces, p.workspaceRepo, p.plans, payments, gateway, engine, p.limiter, p.tx, p.clock);
    settle = new SettlePaymentUseCase(payments, gateway, engine, p.workspaces, p.tx, p.clock);
    overview = new BillingOverviewUseCase(p.workspaces, p.workspaceRepo, p.plans, subscriptions, payments, invoices, pdf, engine, p.tx, p.clock);
    lifecycle = new BillingLifecycleUseCase(subscriptions, p.workspaceRepo, p.plans, p.users, payments, p.mail, p.tx, p.clock);
    admin = new AdminBillingUseCase(subscriptions, payments, invoices, coupons, p.plans, p.workspaceRepo, pdf, engine, p.tx, p.clock);
    ada = p.verifiedUser("ada@example.com");
  }

  CheckoutUseCase.Command pro(String cycle) {
    return new CheckoutUseCase.Command("pro", cycle, "mtn_momo", "237670000001", null, null);
  }

  String planOf(User u) {
    return p.workspaces.ensureFor(u).planCode();
  }

  // ---- Prix ----
  @Test
  void quarterlyAndAnnualPricesFollowTheGridRoundedToTenFrancs() {
    assertThat(Pricing.quarterly(4999)).isEqualTo(13_500);
    assertThat(Pricing.annual(4999)).isEqualTo(41_990);
    assertThat(Pricing.quarterly(14999)).isEqualTo(40_500);
    assertThat(Pricing.annual(14999)).isEqualTo(125_990);
  }

  @Test
  void vatIsExtractedFromTheTaxInclusivePrice() {
    Vat.Split split = Vat.split(4999, 1925);
    assertThat(split.vat()).isEqualTo(807);
    assertThat(split.net() + split.vat()).isEqualTo(4999);
    assertThat(Vat.split(0, 1925).vat()).isZero();
  }

  // ---- Achat ----
  @Test
  void mobileMoneyPurchaseStaysPendingUntilTheProviderConfirms() {
    var started = checkout.start(ada, pro("monthly"));
    assertThat(started.payment().status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(started.payment().amountXaf()).isEqualTo(4999);
    assertThat(started.payment().phoneHint()).isEqualTo("0001");
    assertThat(planOf(ada)).isEqualTo("starter");

    gateway.state = PaymentGatewayPort.State.PENDING;
    assertThat(settle.status(ada, started.payment().id()).status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(planOf(ada)).isEqualTo("starter");
  }

  @Test
  void confirmedPaymentActivatesTheSubscriptionIssuesAnInvoiceAndSendsTheReceipt() {
    var started = checkout.start(ada, pro("monthly"));
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    Payment paid = settle.status(ada, started.payment().id());

    assertThat(paid.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(planOf(ada)).isEqualTo("pro");
    Subscription sub = subscriptions.byWorkspace.values().iterator().next();
    assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.periodEnd()).isEqualTo(Instant.parse("2026-04-10T10:00:00Z"));
    Invoice invoice = invoices.byPayment.get(paid.id());
    assertThat(invoice.number()).isEqualTo("BEEP-2026-000001");
    assertThat(invoice.grossXaf()).isEqualTo(4999);
    assertThat(invoice.vatXaf()).isEqualTo(807);
    assertThat(invoice.vatRateBp()).isEqualTo(1925);
    assertThat(p.mail.sent).contains("receipt:ada@example.com:BEEP-2026-000001");
  }

  @Test
  void settlingTwiceNeverExtendsTwiceNorIssuesTwoInvoices() {
    var started = checkout.start(ada, pro("quarterly"));
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    settle.settle(started.payment().id());
    settle.settle(started.payment().id());
    settle.onNotification("{\"reference\":\"" + started.payment().providerRef() + "\"}", "ok");

    assertThat(invoices.byPayment).hasSize(1);
    assertThat(subscriptions.byWorkspace.values().iterator().next().periodEnd()).isEqualTo(Instant.parse("2026-06-10T10:00:00Z"));
    assertThat(p.mail.sent.stream().filter(s -> s.startsWith("receipt:")).count()).isEqualTo(1);
  }

  @Test
  void declinedPaymentChangesNothing() {
    var started = checkout.start(ada, pro("monthly"));
    gateway.state = PaymentGatewayPort.State.FAILED;
    Payment failed = settle.settle(started.payment().id());
    assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(planOf(ada)).isEqualTo("starter");
    assertThat(invoices.byPayment).isEmpty();
  }

  @Test
  void pendingPaymentExpiresAfterTheTimeout() {
    var started = checkout.start(ada, pro("monthly"));
    gateway.state = PaymentGatewayPort.State.PENDING;
    p.now = p.now.plus(Duration.ofMinutes(31));
    assertThat(settle.settle(started.payment().id()).status()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void providerOutageLeavesThePaymentPendingForTheReconciliationJob() {
    var started = checkout.start(ada, pro("monthly"));
    gateway.down = true;
    assertThat(settle.settle(started.payment().id()).status()).isEqualTo(PaymentStatus.PENDING);
    gateway.down = false;
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    assertThat(settle.settle(started.payment().id()).status()).isEqualTo(PaymentStatus.SUCCEEDED);
  }

  @Test
  void gatewayFailureAtCheckoutMarksThePaymentFailed() {
    gateway.down = true;
    assertThatThrownBy(() -> checkout.start(ada, pro("monthly"))).hasMessage("payment_unavailable");
    assertThat(payments.byId.values()).allMatch(x -> x.status() == PaymentStatus.FAILED);
  }

  @Test
  void notificationsNeedAValidSignature() {
    var started = checkout.start(ada, pro("monthly"));
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    assertThatThrownBy(() -> settle.onNotification("{\"reference\":\"" + started.payment().providerRef() + "\"}", "forged")).hasMessage("unauthorized");
    assertThatThrownBy(() -> settle.onNotification("{}", null)).hasMessage("unauthorized");
    assertThat(planOf(ada)).isEqualTo("starter");
    settle.onNotification("{\"reference\":\"" + started.payment().providerRef() + "\"}", "ok");
    assertThat(planOf(ada)).isEqualTo("pro");
  }

  @Test
  void aWorkspaceCannotReadSomeoneElsesPayment() {
    var started = checkout.start(ada, pro("monthly"));
    User eve = p.verifiedUser("eve@example.com");
    assertThatThrownBy(() -> settle.status(eve, started.payment().id())).hasMessage("not_found");
  }

  @Test
  void purchaseValidation() {
    assertThatThrownBy(() -> checkout.start(ada, new CheckoutUseCase.Command("starter", "monthly", "mtn_momo", "237670000001", null, null))).hasMessage("plan_not_purchasable");
    assertThatThrownBy(() -> checkout.start(ada, new CheckoutUseCase.Command("pro", "weekly", "mtn_momo", "237670000001", null, null))).hasMessage("invalid_cycle");
    assertThatThrownBy(() -> checkout.start(ada, new CheckoutUseCase.Command("pro", "monthly", "manual", "237670000001", null, null))).hasMessage("invalid_method");
    assertThatThrownBy(() -> checkout.start(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "12", null, null))).hasMessage("invalid_phone");
    assertThatThrownBy(() -> checkout.start(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", null, "FR"))).hasMessage("unsupported_country");
    p.plans.published.put("pro", new Plan("pro", "Pro", 1, 4999, false, PRO_RIGHTS));
    assertThatThrownBy(() -> checkout.start(ada, pro("monthly"))).hasMessage("plan_not_purchasable");
  }

  @Test
  void checkoutIsRateLimited() {
    for (int i = 0; i < 10; i++) checkout.start(ada, pro("monthly"));
    assertThatThrownBy(() -> checkout.start(ada, pro("monthly"))).hasMessage("rate_limited");
  }

  // ---- Renouvellement, prorata, coupons ----
  @Test
  void earlyRenewalStacksOnTheCurrentPeriodEnd() {
    pay("monthly");
    p.now = p.now.plus(Duration.ofDays(10));
    pay("monthly");
    assertThat(subscriptions.byWorkspace.values().iterator().next().periodEnd()).isEqualTo(Instant.parse("2026-05-10T10:00:00Z"));
  }

  @Test
  void upgradeIsImmediateAndCreditsTheRemainingTime() {
    pay("monthly");
    p.now = p.now.plus(Duration.ofDays(15));
    var quote = checkout.quote(ada, new CheckoutUseCase.Command("elite", "monthly", "mtn_momo", "237670000001", null, null));
    assertThat(quote.listPriceXaf()).isEqualTo(14_999);
    assertThat(quote.creditXaf()).isBetween(2_400L, 2_700L);
    assertThat(quote.amountXaf()).isEqualTo(14_999 - quote.creditXaf());

    var started = checkout.start(ada, new CheckoutUseCase.Command("elite", "monthly", "mtn_momo", "237670000001", null, null));
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    settle.settle(started.payment().id());
    assertThat(planOf(ada)).isEqualTo("elite");
    Subscription sub = subscriptions.byWorkspace.values().iterator().next();
    assertThat(sub.periodStart()).isEqualTo(p.now);
    assertThat(sub.monthlyRateXaf()).isEqualTo(14_999); // le crédit ne baisse pas la valeur de la période
  }

  @Test
  void aCheaperPlanWaitsForTheEndOfThePaidPeriod() {
    pay("monthly", "elite");
    assertThatThrownBy(() -> checkout.quote(ada, pro("monthly"))).hasMessage("plan_change_at_period_end");
  }

  @Test
  void couponsDiscountTheListPriceAndAreConsumedOnPayment() {
    admin.createCoupon("welcome20", 20, 0, 1, null, Set.of("pro"));
    var cmd = new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", "welcome20", null);
    var quote = checkout.quote(ada, cmd);
    assertThat(quote.discountXaf()).isEqualTo(999);
    assertThat(quote.amountXaf()).isEqualTo(4_000);

    var started = checkout.start(ada, cmd);
    assertThat(coupons.byCode.get("WELCOME20").redeemed()).isZero(); // consommé au règlement, pas à la demande
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    settle.settle(started.payment().id());
    assertThat(coupons.byCode.get("WELCOME20").redeemed()).isEqualTo(1);

    User eve = p.verifiedUser("eve@example.com");
    assertThatThrownBy(() -> checkout.quote(eve, cmd)).hasMessage("coupon_exhausted");
  }

  @Test
  void couponRulesAreEnforced() {
    admin.createCoupon("ELITE-ONLY", 0, 1000, null, null, Set.of("elite"));
    admin.createCoupon("OLD", 10, 0, null, p.now.plus(Duration.ofDays(1)), Set.of());
    assertThatThrownBy(() -> checkout.quote(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", "elite-only", null))).hasMessage("coupon_not_applicable");
    assertThatThrownBy(() -> checkout.quote(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", "nope", null))).hasMessage("invalid_coupon");
    p.now = p.now.plus(Duration.ofDays(2));
    assertThatThrownBy(() -> checkout.quote(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", "old", null))).hasMessage("coupon_expired");
    admin.setCouponActive("old", false);
    assertThatThrownBy(() -> admin.createCoupon("both", 10, 500, null, null, Set.of())).hasMessage("invalid_coupon");
    assertThatThrownBy(() -> admin.createCoupon("old", 5, 0, null, null, Set.of())).hasMessage("coupon_exists");
  }

  @Test
  void aFullDiscountActivatesThePeriodWithoutCallingTheProvider() {
    admin.createCoupon("FREEMONTH", 100, 0, null, null, Set.of());
    var result = checkout.start(ada, new CheckoutUseCase.Command("pro", "monthly", "mtn_momo", "237670000001", "freemonth", null));
    assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(result.invoice().grossXaf()).isZero();
    assertThat(gateway.initiated).isZero();
    assertThat(planOf(ada)).isEqualTo("pro");
  }

  // ---- Essai Elite ----
  @Test
  void eliteTrialLastsFourteenDaysOnceAndFallsBackToStarter() {
    Subscription trial = overview.startTrial(ada);
    assertThat(planOf(ada)).isEqualTo("elite");
    assertThat(trial.periodEnd()).isEqualTo(p.now.plus(Duration.ofDays(14)));
    assertThatThrownBy(() -> overview.startTrial(ada)).hasMessage("trial_already_used");

    p.now = p.now.plus(Duration.ofDays(13));
    lifecycle.run();
    assertThat(p.mail.sent).contains("reminder:ada@example.com:d1");
    assertThat(planOf(ada)).isEqualTo("elite");

    p.now = p.now.plus(Duration.ofDays(2));
    lifecycle.run();
    assertThat(planOf(ada)).isEqualTo("starter"); // pas de période de grâce pour un essai
    assertThat(p.mail.sent).contains("expired:ada@example.com");
    assertThatThrownBy(() -> overview.startTrial(ada)).hasMessage("trial_already_used");
  }

  // ---- Cycle de vie ----
  @Test
  void remindersGraceAndExpiryFollowTheCalendar() {
    pay("monthly");
    Instant end = subscriptions.byWorkspace.values().iterator().next().periodEnd();

    p.now = end.minus(Duration.ofDays(8));
    assertThat(lifecycle.run().reminders()).isZero();

    p.now = end.minus(Duration.ofDays(6));
    assertThat(lifecycle.run().reminders()).isEqualTo(1);
    assertThat(lifecycle.run().reminders()).isZero(); // un seul rappel par palier

    p.now = end.minus(Duration.ofDays(2));
    lifecycle.run();
    p.now = end.minus(Duration.ofHours(10));
    lifecycle.run();
    assertThat(p.mail.sent.stream().filter(s -> s.startsWith("reminder:")).toList())
        .containsExactly("reminder:ada@example.com:d7", "reminder:ada@example.com:d3", "reminder:ada@example.com:d1");

    p.now = end.plus(Duration.ofHours(1));
    assertThat(lifecycle.run().graced()).isEqualTo(1);
    assertThat(p.mail.sent).contains("grace:ada@example.com");
    assertThat(planOf(ada)).isEqualTo("pro"); // accès complet pendant la grâce

    p.now = end.plus(Duration.ofDays(6));
    assertThat(lifecycle.run().expired()).isZero();
    assertThat(planOf(ada)).isEqualTo("pro");

    p.now = end.plus(Duration.ofDays(7)).plus(Duration.ofMinutes(1));
    assertThat(lifecycle.run().expired()).isEqualTo(1);
    assertThat(planOf(ada)).isEqualTo("starter");
    assertThat(p.mail.sent).contains("expired:ada@example.com");
    assertThat(lifecycle.run().expired()).isZero();
  }

  @Test
  void paymentDuringGraceReactivatesFromNow() {
    pay("monthly");
    Instant end = subscriptions.byWorkspace.values().iterator().next().periodEnd();
    p.now = end.plus(Duration.ofDays(2));
    lifecycle.run();
    assertThat(subscriptions.byWorkspace.values().iterator().next().status()).isEqualTo(SubscriptionStatus.GRACE);
    pay("monthly");
    Subscription sub = subscriptions.byWorkspace.values().iterator().next();
    assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.periodStart()).isEqualTo(p.now);
    assertThat(sub.notices()).isEmpty();
  }

  @Test
  void aMailFailureIsRetriedOnTheNextRun() {
    pay("monthly");
    Instant end = subscriptions.byWorkspace.values().iterator().next().periodEnd();
    p.now = end.minus(Duration.ofDays(2));
    p.mail.sent.clear();
    BillingLifecycleUseCase failing = new BillingLifecycleUseCase(subscriptions, p.workspaceRepo, p.plans, p.users, payments, new io.beep.core.app.auth.Fakes.Mail() {
      @Override
      public void sendRenewalReminder(io.beep.core.domain.user.Email to, String locale, String planName, boolean trial, int daysLeft, Instant dueAt) {
        throw new IllegalStateException("smtp down");
      }
    }, p.tx, p.clock);
    assertThat(failing.run().mailFailures()).isEqualTo(1);
    assertThat(lifecycle.run().reminders()).isEqualTo(1);
  }

  // ---- Staff ----
  @Test
  void manualPaymentExtendsTheSubscriptionAndIssuesAnInvoice() {
    var ws = p.workspaces.ensureFor(ada);
    var applied = admin.recordManualPayment(ws.id(), "pro", "annual", 41_990, "Virement UBA 2026-03-10", "CM", "staff-1");
    assertThat(planOf(ada)).isEqualTo("pro");
    assertThat(applied.subscription().periodEnd()).isEqualTo(Instant.parse("2027-03-10T10:00:00Z"));
    assertThat(applied.payment().method().db()).isEqualTo("manual");
    assertThat(applied.invoice().grossXaf()).isEqualTo(41_990);
    assertThat(p.mail.sent).contains("receipt:ada@example.com:BEEP-2026-000001");
    assertThatThrownBy(() -> admin.recordManualPayment(ws.id(), "starter", "annual", 100, "x ref", "CM", "s")).hasMessage("plan_not_purchasable");
    assertThatThrownBy(() -> admin.recordManualPayment(ws.id(), "pro", "annual", 0, "x ref", "CM", "s")).hasMessage("invalid_amount");
  }

  @Test
  void staffCanGrantAPlanAndRevertToStarter() {
    var ws = p.workspaces.ensureFor(ada);
    admin.changePlan(ws.id(), "elite", p.now.plus(Duration.ofDays(60)), "Partenaire");
    assertThat(planOf(ada)).isEqualTo("elite");
    assertThatThrownBy(() -> admin.changePlan(ws.id(), "elite", p.now.minus(Duration.ofDays(1)), "Partenaire")).hasMessage("invalid_request");
    assertThatThrownBy(() -> admin.changePlan(ws.id(), "elite", p.now.plus(Duration.ofDays(60)), "")).isInstanceOf(io.beep.core.domain.DomainException.class);
    admin.changePlan(ws.id(), "starter", null, "Fin du partenariat");
    assertThat(planOf(ada)).isEqualTo("starter");
    assertThat(lifecycle.run().expired()).isZero();
    assertThat(p.mail.sent).doesNotContain("expired:ada@example.com"); // retour décidé par le staff : pas de message d'expiration
  }

  @Test
  void summaryComputesMrrFromActiveSubscriptionsOnly() {
    pay("annual");
    User eve = p.verifiedUser("eve@example.com");
    overview.startTrial(eve);
    var s = admin.summary();
    assertThat(s.subscriptions().active()).isEqualTo(1);
    assertThat(s.subscriptions().trialing()).isEqualTo(1);
    assertThat(s.subscriptions().mrrXaf()).isEqualTo(Pricing.monthlyEquivalent(41_990 - 0, BillingCycle.ANNUAL));
    assertThat(s.arrXaf()).isEqualTo(s.subscriptions().mrrXaf() * 12);
  }

  @Test
  void publishingAPlanBumpsItsVersionAndValidatesTheDraft() {
    Plan v2 = admin.publishPlan(new AdminBillingUseCase.PlanDraft("pro", "Pro", 5499, null, null, true, Map.of("links.stock_max", 60L)), "staff-1");
    assertThat(v2.version()).isEqualTo(2);
    assertThat(v2.priceQuarterlyXaf()).isEqualTo(Pricing.quarterly(5499));
    assertThat(p.plans.published.get("pro").priceXaf()).isEqualTo(5499);
    assertThatThrownBy(() -> admin.publishPlan(new AdminBillingUseCase.PlanDraft("pro", "Pro", 5499, null, null, true, Map.of("nope", 1L)), "s")).hasMessage("unknown_entitlement");
    assertThatThrownBy(() -> admin.publishPlan(new AdminBillingUseCase.PlanDraft("starter", "Starter", 100, null, null, true, Map.of()), "s")).hasMessage("invalid_plan");
    assertThatThrownBy(() -> admin.publishPlan(new AdminBillingUseCase.PlanDraft("Bad Code", "X", 0, null, null, true, Map.of()), "s")).hasMessage("invalid_plan");
  }

  // ---- Aides ----
  void pay(String cycle) {
    pay(cycle, "pro");
  }

  void pay(String cycle, String plan) {
    var started = checkout.start(ada, new CheckoutUseCase.Command(plan, cycle, "mtn_momo", "237670000001", null, null));
    gateway.state = PaymentGatewayPort.State.SUCCEEDED;
    settle.settle(started.payment().id());
  }

  // ---- Doublures ----
  static class Gateway implements PaymentGatewayPort {
    State state = State.PENDING;
    boolean down;
    int initiated;

    public String name() { return "fake"; }

    public Charge initiate(ChargeRequest r) {
      if (down) throw new GatewayException("down", null);
      initiated++;
      return new Charge("ref-" + r.reference(), null);
    }

    public Verification verify(String providerRef) {
      if (down) throw new GatewayException("down", null);
      return new Verification(state, state == State.FAILED ? "declined" : null);
    }

    public boolean validSignature(String rawBody, String signature) { return "ok".equals(signature); }

    public String referenceOf(String rawBody) {
      int i = rawBody.indexOf("\"reference\":\"");
      return i < 0 ? null : rawBody.substring(i + 13, rawBody.indexOf('"', i + 13));
    }
  }

  class MemSubscriptions implements SubscriptionRepository {
    final Map<UUID, Subscription> byWorkspace = new LinkedHashMap<>();

    public Optional<Subscription> findByWorkspace(UUID id) { return Optional.ofNullable(byWorkspace.get(id)); }
    public Optional<Subscription> lockByWorkspace(UUID id) { return findByWorkspace(id); }
    public void save(Subscription s) { byWorkspace.put(s.workspaceId(), s); }
    public List<Subscription> candidates(Instant horizon, int limit) {
      return byWorkspace.values().stream().filter(s -> s.status() != SubscriptionStatus.EXPIRED ? !s.periodEnd().isAfter(horizon)
          : s.periodEnd().isAfter(s.createdAt()) && !s.notices().contains(Subscription.NOTICE_EXPIRED)).toList();
    }
    public List<Row> page(SubscriptionStatus status, int limit, int offset) { return List.of(); }
    public Summary summary() {
      long[] c = new long[4];
      long mrr = 0;
      for (Subscription s : byWorkspace.values()) {
        c[s.status().ordinal()]++;
        mrr += s.mrrXaf();
      }
      return new Summary(c[0], c[1], c[2], c[3], mrr);
    }
    public List<Row> upcoming(Instant now, Instant until, int limit) { return List.of(); }
  }

  class MemPayments implements PaymentRepository {
    final Map<UUID, Payment> byId = new LinkedHashMap<>();

    public void insert(Payment x) {
      if (byId.values().stream().anyMatch(o -> o.provider().equals(x.provider()) && o.providerRef().equals(x.providerRef()))) throw new DuplicateReferenceException(null);
      byId.put(x.id(), x);
    }
    public Optional<Payment> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<Payment> findByProviderRef(String provider, String ref) {
      return byId.values().stream().filter(x -> x.provider().equals(provider) && x.providerRef().equals(ref)).findFirst();
    }
    public Optional<Payment> lockById(UUID id) { return findById(id); }
    public void update(Payment x) { byId.put(x.id(), x); }
    public List<Payment> forWorkspace(UUID ws, int limit) { return byId.values().stream().filter(x -> x.workspaceId().equals(ws)).toList(); }
    public List<Payment> pendingBefore(Instant before, int limit) {
      return byId.values().stream().filter(x -> x.status() == PaymentStatus.PENDING && x.createdAt().isBefore(before)).toList();
    }
    public Page page(PaymentStatus status, UUID ws, int limit, String cursor) { return new Page(new ArrayList<>(), null); }
    public long succeededSince(Instant since) { return byId.values().stream().filter(x -> x.status() == PaymentStatus.SUCCEEDED).mapToLong(Payment::amountXaf).sum(); }
    public long countByStatus(PaymentStatus status) { return byId.values().stream().filter(x -> x.status() == status).count(); }
  }

  class MemInvoices implements InvoiceRepository {
    final Map<UUID, Invoice> byPayment = new HashMap<>();
    long seq;

    public Invoice issue(Invoice d) {
      return byPayment.computeIfAbsent(d.paymentId(), k -> new Invoice(Invoice.format(2026, ++seq), d.paymentId(), d.workspaceId(), d.issuedAt(), d.customerName(), d.customerEmail(),
          d.country(), d.planName(), d.cycle(), d.periodStart(), d.periodEnd(), d.listPriceXaf(), d.discountXaf(), d.creditXaf(), d.grossXaf(), d.netXaf(), d.vatXaf(), d.vatRateBp(),
          d.method()));
    }
    public Optional<Invoice> findByNumber(String number) { return byPayment.values().stream().filter(i -> i.number().equals(number)).findFirst(); }
    public Optional<Invoice> findByPayment(UUID id) { return Optional.ofNullable(byPayment.get(id)); }
    public List<Invoice> forWorkspace(UUID ws, int limit) { return byPayment.values().stream().filter(i -> i.workspaceId().equals(ws)).toList(); }
  }

  class MemCoupons implements CouponRepository {
    final Map<String, Coupon> byCode = new LinkedHashMap<>();

    public Optional<Coupon> find(String code) { return Optional.ofNullable(byCode.get(code)); }
    public Optional<Coupon> lock(String code) { return find(code); }
    public void insert(Coupon c) {
      if (byCode.putIfAbsent(c.code(), c) != null) throw new DuplicateCodeException(null);
    }
    public void update(Coupon c) { byCode.put(c.code(), c); }
    public List<Coupon> list(int limit) { return List.copyOf(byCode.values()); }
  }
}
