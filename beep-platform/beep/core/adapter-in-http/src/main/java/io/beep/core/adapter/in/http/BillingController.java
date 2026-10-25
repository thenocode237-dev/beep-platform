package io.beep.core.adapter.in.http;

import io.beep.core.app.billing.BillingOverviewUseCase;
import io.beep.core.app.billing.CheckoutUseCase;
import io.beep.core.app.billing.Quote;
import io.beep.core.app.billing.SettlePaymentUseCase;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.plan.Plan;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abonnement de l'espace client (/v1/billing) : état, devis, achat par Mobile Money ou carte, suivi du paiement, essai Elite, factures PDF.
 * La notification de l'agrégateur arrive sur POST /v1/billing/webhook (publique, authentifiée par la signature du corps brut).
 */
@RestController
@RequestMapping("/v1/billing")
@Profile("!worker & !migrate")
public class BillingController {
  record CheckoutRequest(String plan, String cycle, String method, String phone, String coupon, String country) {}

  record PlanView(String code, String name, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf, @com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic) {}

  record SubscriptionView(String plan, String status, String cycle, Instant periodStart, Instant periodEnd, Instant graceEnd, boolean paid) {}

  record PaymentView(UUID id, String plan, String cycle, String method, String status, long amountXaf, long listPriceXaf, long discountXaf, long creditXaf, String coupon,
      String failureReason, String invoice, Instant createdAt, Instant paidAt) {}

  record InvoiceView(String number, String plan, String cycle, long grossXaf, long netXaf, long vatXaf, int vatRateBp, Instant issuedAt, Instant periodStart, Instant periodEnd) {}

  record BillingView(PlanView plan, SubscriptionView subscription, List<PlanView> plans, List<PaymentView> payments, List<InvoiceView> invoices, boolean trialAvailable,
      Map<String, Integer> vatRatesBp) {}

  record QuoteView(String plan, String cycle, long listPriceXaf, long discountXaf, long creditXaf, long amountXaf, String coupon, Instant newPeriodEnd) {}

  record CheckoutView(PaymentView payment, String redirectUrl) {}

  private final BillingOverviewUseCase overview;
  private final CheckoutUseCase checkout;
  private final SettlePaymentUseCase settle;
  private final SessionCookies cookies;

  public BillingController(BillingOverviewUseCase overview, CheckoutUseCase checkout, SettlePaymentUseCase settle, SessionCookies cookies) {
    this.overview = overview;
    this.checkout = checkout;
    this.settle = settle;
    this.cookies = cookies;
  }

  @GetMapping
  BillingView get(HttpServletRequest req) {
    var o = overview.execute(cookies.requireUser(req));
    var invoiceNumbers = new java.util.HashMap<UUID, String>();
    o.invoices().forEach(i -> invoiceNumbers.put(i.paymentId(), i.number()));
    return new BillingView(plan(o.plan()), o.hasSubscription() ? subscription(o.subscription(), o.now()) : null, o.plans().stream().map(BillingController::plan).toList(),
        o.payments().stream().map(p -> payment(p, invoiceNumbers.get(p.id()))).toList(), o.invoices().stream().map(BillingController::invoice).toList(), o.trialAvailable(),
        overview.engine().settings().vatRatesBp());
  }

  @PostMapping(value = "/quote", consumes = MediaType.APPLICATION_JSON_VALUE)
  QuoteView quote(@RequestBody CheckoutRequest r, HttpServletRequest req) {
    return quote(checkout.quote(cookies.requireUser(req), new CheckoutUseCase.Command(r.plan(), r.cycle(), r.method(), r.phone(), r.coupon(), r.country())));
  }

  @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<CheckoutView> checkout(@RequestBody CheckoutRequest r, HttpServletRequest req) {
    var result = checkout.start(cookies.requireUser(req), new CheckoutUseCase.Command(r.plan(), r.cycle(), r.method(), r.phone(), r.coupon(), r.country()));
    String invoice = result.invoice() == null ? null : result.invoice().number();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new CheckoutView(payment(result.payment(), invoice), result.redirectUrl()));
  }

  /** Interrogé après la confirmation sur le téléphone : vérifie auprès de l'agrégateur et règle le paiement si besoin. */
  @GetMapping("/payments/{id}")
  PaymentView payment(@PathVariable UUID id, HttpServletRequest req) {
    return payment(settle.status(cookies.requireUser(req), id), null);
  }

  @PostMapping("/trial")
  ResponseEntity<SubscriptionView> trial(HttpServletRequest req) {
    Subscription s = overview.startTrial(cookies.requireUser(req));
    return ResponseEntity.status(HttpStatus.CREATED).body(subscription(s, Instant.now()));
  }

  @GetMapping("/invoices/{number}/pdf")
  ResponseEntity<byte[]> invoicePdf(@PathVariable String number, HttpServletRequest req) {
    byte[] pdf = overview.invoicePdf(cookies.requireUser(req), number);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + number.replaceAll("[^A-Za-z0-9-]", "") + ".pdf\"")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(pdf);
  }

  /** Notification de l'agrégateur : le corps est lu brut (la signature porte sur ces octets). Toujours 200 si la signature est valide. */
  @PostMapping(value = "/webhook", consumes = MediaType.ALL_VALUE)
  ResponseEntity<Void> webhook(@RequestBody String raw, @RequestHeader(value = "x-notch-signature", required = false) String notch,
      @RequestHeader(value = "x-beep-signature", required = false) String sandbox) {
    settle.onNotification(raw, notch != null ? notch : sandbox);
    return ResponseEntity.ok().build();
  }

  // ---- Vues ----
  static PlanView plan(Plan p) {
    return new PlanView(p.code(), p.name(), p.priceXaf(), p.priceQuarterlyXaf(), p.priceAnnualXaf(), p.publicPlan());
  }

  static SubscriptionView subscription(Subscription s, Instant now) {
    return new SubscriptionView(s.planCode(), s.dueStatus(now).db(), s.cycle() == null ? null : s.cycle().db(), s.periodStart(), s.periodEnd(), s.graceEnd(), s.monthlyRateXaf() > 0);
  }

  static PaymentView payment(Payment p, String invoice) {
    return new PaymentView(p.id(), p.planCode(), p.cycle().db(), p.method().db(), p.status().db(), p.amountXaf(), p.listPriceXaf(), p.discountXaf(), p.creditXaf(), p.couponCode(),
        p.failureReason(), invoice, p.createdAt(), p.paidAt());
  }

  static InvoiceView invoice(Invoice i) {
    return new InvoiceView(i.number(), i.planName(), i.cycle().db(), i.grossXaf(), i.netXaf(), i.vatXaf(), i.vatRateBp(), i.issuedAt(), i.periodStart(), i.periodEnd());
  }

  static QuoteView quote(Quote q) {
    return new QuoteView(q.planCode(), q.cycle().db(), q.listPriceXaf(), q.discountXaf(), q.creditXaf(), q.amountXaf(), q.couponCode(), q.newPeriodEnd());
  }
}
