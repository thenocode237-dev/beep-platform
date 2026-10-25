package io.beep.core.adapter.in.admin;

import io.beep.core.app.billing.AdminBillingUseCase;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.domain.billing.Coupon;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.plan.Plan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Facturation côté staff : /internal/admin/v1/billing, paiements manuels, changement de plan, coupons, publication de plans. */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminBillingController {
  record SummaryView(long trialing, long active, long grace, long expired, long mrrXaf, long arrXaf, long revenue30dXaf, long pendingPayments, long failedPayments) {}

  record SubscriptionView(UUID workspaceId, String workspaceName, String ownerEmail, String plan, String status, String cycle, Instant periodStart, Instant periodEnd,
      Instant graceEnd, long monthlyRateXaf) {}

  record SubscriptionsList(List<SubscriptionView> items) {}

  record PaymentView(UUID id, UUID workspaceId, String workspaceName, String ownerEmail, String plan, String cycle, String method, String provider, String status, long amountXaf,
      long listPriceXaf, long discountXaf, long creditXaf, String coupon, String country, String note, String recordedBy, String failureReason, Instant createdAt, Instant paidAt) {}

  record PaymentsPage(List<PaymentView> items, String nextCursor) {}

  record InvoiceView(String number, UUID paymentId, String plan, String cycle, long grossXaf, long netXaf, long vatXaf, int vatRateBp, String country, Instant issuedAt) {}

  record ManualPayment(String plan, String cycle, long amountXaf, String reference, String country) {}

  record ManualPaymentResult(PaymentView payment, InvoiceView invoice, Instant validUntil) {}

  record PlanChange(String plan, Instant until, String reason) {}

  record CouponView(String code, int percentOff, long amountOffXaf, Integer maxRedemptions, int redeemed, Instant validUntil, Set<String> plans, boolean active, Instant createdAt) {}

  record CouponsList(List<CouponView> items) {}

  record CouponCreate(String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, Instant validUntil, Set<String> plans) {}

  record CouponActive(boolean active) {}

  record PlanPublish(String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, @com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic,
      Map<String, Long> entitlements) {}

  record PlanView(String code, String name, int version, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf,
      @com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic, Map<String, Long> entitlements) {}

  private final AdminBillingUseCase billing;

  public AdminBillingController(AdminBillingUseCase billing) {
    this.billing = billing;
  }

  @GetMapping("/billing/summary")
  SummaryView summary() {
    var s = billing.summary();
    var sub = s.subscriptions();
    return new SummaryView(sub.trialing(), sub.active(), sub.grace(), sub.expired(), sub.mrrXaf(), s.arrXaf(), s.revenue30dXaf(), s.pendingPayments(), s.failedPayments());
  }

  @GetMapping("/billing/subscriptions")
  SubscriptionsList subscriptions(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
    return new SubscriptionsList(billing.subscriptions(status, limit, offset).stream().map(AdminBillingController::subscription).toList());
  }

  /** Échéances à venir (essais et périodes payées), les plus proches d'abord. */
  @GetMapping("/billing/upcoming")
  SubscriptionsList upcoming(@RequestParam(defaultValue = "14") int days, @RequestParam(defaultValue = "50") int limit) {
    return new SubscriptionsList(billing.upcoming(days, limit).stream().map(AdminBillingController::subscription).toList());
  }

  @GetMapping("/billing/payments")
  PaymentsPage payments(@RequestParam(required = false) String status, @RequestParam(required = false) UUID workspaceId, @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    PaymentRepository.Page page = billing.payments(status, workspaceId, limit, cursor);
    return new PaymentsPage(page.items().stream().map(r -> payment(r.payment(), r.workspaceName(), r.ownerEmail())).toList(), page.nextCursor());
  }

  @GetMapping("/workspaces/{id}/invoices")
  List<InvoiceView> invoices(@PathVariable UUID id) {
    return billing.invoices(id).stream().map(AdminBillingController::invoice).toList();
  }

  @GetMapping("/billing/invoices/{number}/pdf")
  ResponseEntity<byte[]> invoicePdf(@PathVariable String number) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + number.replaceAll("[^A-Za-z0-9-]", "") + ".pdf\"").body(billing.invoicePdf(number));
  }

  @PostMapping(value = "/workspaces/{id}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ManualPaymentResult> manualPayment(@PathVariable UUID id, @RequestBody ManualPayment r, @RequestHeader(value = "X-Staff-Id", required = false) String staff) {
    var applied = billing.recordManualPayment(id, r.plan(), r.cycle(), r.amountXaf(), r.reference(), r.country(), AdminViews.staff(staff));
    return ResponseEntity.status(HttpStatus.CREATED).body(new ManualPaymentResult(payment(applied.payment(), null, null), invoice(applied.invoice()), applied.subscription().periodEnd()));
  }

  @PutMapping(value = "/workspaces/{id}/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
  SubscriptionView changePlan(@PathVariable UUID id, @RequestBody PlanChange r) {
    Subscription s = billing.changePlan(id, r.plan(), r.until(), r.reason());
    return new SubscriptionView(s.workspaceId(), null, null, s.planCode(), s.status().db(), s.cycle() == null ? null : s.cycle().db(), s.periodStart(), s.periodEnd(), s.graceEnd(),
        s.monthlyRateXaf());
  }

  @GetMapping("/coupons")
  CouponsList coupons() {
    return new CouponsList(billing.coupons().stream().map(AdminBillingController::coupon).toList());
  }

  @PostMapping(value = "/coupons", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<CouponView> createCoupon(@RequestBody CouponCreate r) {
    Coupon c = billing.createCoupon(r.code(), r.percentOff() == null ? 0 : r.percentOff(), r.amountOffXaf() == null ? 0 : r.amountOffXaf(), r.maxRedemptions(), r.validUntil(),
        r.plans());
    return ResponseEntity.status(HttpStatus.CREATED).body(coupon(c));
  }

  @PutMapping(value = "/coupons/{code}/active", consumes = MediaType.APPLICATION_JSON_VALUE)
  CouponView couponActive(@PathVariable String code, @RequestBody CouponActive r) {
    return coupon(billing.setCouponActive(code, r.active()));
  }

  /** Publication d'une version de plan depuis un brouillon du back office. */
  @PutMapping(value = "/plans/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
  PlanView publishPlan(@PathVariable String code, @RequestBody PlanPublish r, @RequestHeader(value = "X-Staff-Id", required = false) String staff) {
    Plan p = billing.publishPlan(new AdminBillingUseCase.PlanDraft(code, r.name(), r.priceXaf(), r.priceQuarterlyXaf(), r.priceAnnualXaf(), r.isPublic(), r.entitlements()),
        AdminViews.staff(staff));
    return new PlanView(p.code(), p.name(), p.version(), p.priceXaf(), p.priceQuarterlyXaf(), p.priceAnnualXaf(), p.publicPlan(), AdminViews.sorted(p.entitlements()));
  }

  // ---- Vues ----
  private static SubscriptionView subscription(SubscriptionRepository.Row row) {
    Subscription s = row.subscription();
    return new SubscriptionView(s.workspaceId(), row.workspaceName(), row.ownerEmail(), s.planCode(), s.status().db(), s.cycle() == null ? null : s.cycle().db(), s.periodStart(),
        s.periodEnd(), s.graceEnd(), s.monthlyRateXaf());
  }

  private static PaymentView payment(Payment p, String workspaceName, String ownerEmail) {
    return new PaymentView(p.id(), p.workspaceId(), workspaceName, ownerEmail, p.planCode(), p.cycle().db(), p.method().db(), p.provider(), p.status().db(), p.amountXaf(),
        p.listPriceXaf(), p.discountXaf(), p.creditXaf(), p.couponCode(), p.country(), p.note(), p.recordedBy(), p.failureReason(), p.createdAt(), p.paidAt());
  }

  private static InvoiceView invoice(Invoice i) {
    return i == null ? null : new InvoiceView(i.number(), i.paymentId(), i.planName(), i.cycle().db(), i.grossXaf(), i.netXaf(), i.vatXaf(), i.vatRateBp(), i.country(), i.issuedAt());
  }

  private static CouponView coupon(Coupon c) {
    return new CouponView(c.code(), c.percentOff(), c.amountOffXaf(), c.maxRedemptions(), c.redeemed(), c.validUntil(), new java.util.TreeSet<>(c.planCodes()), c.active(), c.createdAt());
  }
}
