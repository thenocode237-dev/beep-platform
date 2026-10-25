package io.beep.bo.adapter.in.http;

import io.beep.bo.app.billing.BillingUseCase;
import io.beep.bo.app.catalog.CatalogUseCase;
import io.beep.bo.app.domains.DomainsUseCase;
import io.beep.bo.app.plans.PlanDraftsUseCase;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.port.PlanDraftRepository;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 2 · cycle 4 : vue Facturation, paiements manuels, changement de plan, coupons, brouillons et publication des plans. */
@RestController
@RequestMapping("/api")
@Profile("!migrate")
public class BillingConsoleController {
  record Subscriptions(List<CoreGatewayPort.SubscriptionView> items) {}

  record Invoices(List<CoreGatewayPort.InvoiceView> items) {}

  record Coupons(List<CoreGatewayPort.CouponView> items) {}

  record Drafts(List<PlanDraftRepository.Draft> items) {}

  record ManualPaymentRequest(String plan, String cycle, long amountXaf, String reference, String country, String totp) {}

  record PlanChangeRequest(String plan, Instant until, String reason, String totp) {}

  record CouponRequest(String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, Instant validUntil, List<String> plans, String totp) {}

  record CouponActiveRequest(boolean active, String totp) {}

  record DraftRequest(String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, boolean isPublic, Map<String, Long> entitlements, int baseVersion, String note) {}

  record PublishRequest(String totp) {}

  record Apps(List<CoreGatewayPort.AppView> items) {}

  record AppRequest(String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, String totp) {}

  record AppEnabledRequest(boolean enabled, String totp) {}

  record AppTestRequest(String url, String profile, CoreGatewayPort.AppView candidate) {}

  record BlockRequest(String reason, String totp) {}

  record TotpRequest(String totp) {}

  private final BillingUseCase billing;
  private final PlanDraftsUseCase drafts;
  private final StaffSessions sessions;
  private final DomainsUseCase domains;
  private final CatalogUseCase catalog;

  public BillingConsoleController(BillingUseCase billing, PlanDraftsUseCase drafts, StaffSessions sessions, DomainsUseCase domains, CatalogUseCase catalog) {
    this.catalog = catalog;
    this.domains = domains;
    this.billing = billing;
    this.drafts = drafts;
    this.sessions = sessions;
  }

  @GetMapping("/billing/summary")
  CoreGatewayPort.BillingSummary summary(HttpServletRequest req) {
    return billing.summary(sessions.require(req));
  }

  @GetMapping("/billing/subscriptions")
  Subscriptions subscriptions(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
      HttpServletRequest req) {
    return new Subscriptions(billing.subscriptions(sessions.require(req), status, limit, offset));
  }

  @GetMapping("/billing/upcoming")
  Subscriptions upcoming(@RequestParam(defaultValue = "14") int days, @RequestParam(defaultValue = "50") int limit, HttpServletRequest req) {
    return new Subscriptions(billing.upcoming(sessions.require(req), days, limit));
  }

  @GetMapping("/billing/payments")
  CoreGatewayPort.PaymentsPage payments(@RequestParam(required = false) String status, @RequestParam(required = false) UUID workspaceId, @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor, HttpServletRequest req) {
    return billing.payments(sessions.require(req), status, workspaceId, limit, cursor);
  }

  @GetMapping("/workspaces/{id}/invoices")
  Invoices invoices(@PathVariable UUID id, HttpServletRequest req) {
    return new Invoices(billing.invoices(sessions.require(req), id));
  }

  @GetMapping("/billing/invoices/{number}/pdf")
  ResponseEntity<byte[]> invoicePdf(@PathVariable String number, HttpServletRequest req) {
    byte[] pdf = billing.invoicePdf(sessions.require(req), number, req.getRemoteAddr());
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + number.replaceAll("[^A-Za-z0-9-]", "") + ".pdf\"").header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(pdf);
  }

  @PostMapping(value = "/workspaces/{id}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<CoreGatewayPort.ManualPaymentResult> manualPayment(@PathVariable UUID id, @RequestBody ManualPaymentRequest r, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(billing.recordManualPayment(sessions.require(req), id, r.plan(), r.cycle(), r.amountXaf(), r.reference(), r.country(), r.totp(), req.getRemoteAddr()));
  }

  @PutMapping(value = "/workspaces/{id}/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.SubscriptionView changePlan(@PathVariable UUID id, @RequestBody PlanChangeRequest r, HttpServletRequest req) {
    return billing.changePlan(sessions.require(req), id, r.plan(), r.until(), r.reason(), r.totp(), req.getRemoteAddr());
  }

  @GetMapping("/coupons")
  Coupons coupons(HttpServletRequest req) {
    return new Coupons(billing.coupons(sessions.require(req)));
  }

  @PostMapping(value = "/coupons", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<CoreGatewayPort.CouponView> createCoupon(@RequestBody CouponRequest r, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(billing.createCoupon(sessions.require(req), r.code(), r.percentOff(), r.amountOffXaf(), r.maxRedemptions(),
        r.validUntil(), r.plans(), r.totp(), req.getRemoteAddr()));
  }

  @PutMapping(value = "/coupons/{code}/active", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.CouponView couponActive(@PathVariable String code, @RequestBody CouponActiveRequest r, HttpServletRequest req) {
    return billing.setCouponActive(sessions.require(req), code, r.active(), r.totp(), req.getRemoteAddr());
  }

  // ---- Catalogue d'applications ----
  @GetMapping("/catalog")
  Apps catalog(HttpServletRequest req) {
    return new Apps(catalog.list(sessions.require(req)));
  }

  @PutMapping(value = "/catalog/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.AppView publishApp(@PathVariable String code, @RequestBody AppRequest r, HttpServletRequest req) {
    return catalog.publish(sessions.require(req), code, r.name(), r.hosts() == null ? List.of() : r.hosts(), r.iosTemplate(), r.androidPackage(), r.androidTemplate(), r.enabled(), r.totp(),
        req.getRemoteAddr());
  }

  @PutMapping(value = "/catalog/{code}/enabled", consumes = MediaType.APPLICATION_JSON_VALUE)
  Apps appEnabled(@PathVariable String code, @RequestBody AppEnabledRequest r, HttpServletRequest req) {
    return new Apps(catalog.setEnabled(sessions.require(req), code, r.enabled(), r.totp(), req.getRemoteAddr()));
  }

  @PostMapping(value = "/catalog/test", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.AppTestResult testApp(@RequestBody AppTestRequest r, HttpServletRequest req) {
    return catalog.test(sessions.require(req), r.url(), r.profile(), r.candidate());
  }

  // ---- Domaines personnalisés ----
  @GetMapping("/domains")
  CoreGatewayPort.DomainsPage domains(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor, HttpServletRequest req) {
    return domains.list(sessions.require(req), q, status, limit, cursor);
  }

  @GetMapping("/domains/{id}")
  CoreGatewayPort.DomainView domain(@PathVariable UUID id, HttpServletRequest req) {
    return domains.get(sessions.require(req), id);
  }

  @PostMapping("/domains/{id}/recheck")
  CoreGatewayPort.DomainView recheckDomain(@PathVariable UUID id, HttpServletRequest req) {
    return domains.recheck(sessions.require(req), id, req.getRemoteAddr());
  }

  @PostMapping(value = "/domains/{id}/block", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.DomainView blockDomain(@PathVariable UUID id, @RequestBody BlockRequest r, HttpServletRequest req) {
    return domains.block(sessions.require(req), id, r.reason(), r.totp(), req.getRemoteAddr());
  }

  @PostMapping(value = "/domains/{id}/unblock", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.DomainView unblockDomain(@PathVariable UUID id, @RequestBody TotpRequest r, HttpServletRequest req) {
    return domains.unblock(sessions.require(req), id, r.totp(), req.getRemoteAddr());
  }

  // ---- Brouillons de plans ----
  @GetMapping("/plan-drafts")
  Drafts drafts(HttpServletRequest req) {
    return new Drafts(drafts.list(sessions.require(req)));
  }

  @PutMapping(value = "/plan-drafts/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
  PlanDraftRepository.Draft saveDraft(@PathVariable String code, @RequestBody DraftRequest r, HttpServletRequest req) {
    return drafts.save(sessions.require(req), code, r.name(), r.priceXaf(), r.priceQuarterlyXaf(), r.priceAnnualXaf(), r.isPublic(), r.entitlements(), r.baseVersion(),
        r.note(), req.getRemoteAddr());
  }

  @DeleteMapping("/plan-drafts/{code}")
  ResponseEntity<Void> discardDraft(@PathVariable String code, HttpServletRequest req) {
    drafts.discard(sessions.require(req), code, req.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/plan-drafts/{code}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.PlanView publish(@PathVariable String code, @RequestBody PublishRequest r, HttpServletRequest req) {
    return drafts.publish(sessions.require(req), code, r.totp(), req.getRemoteAddr());
  }
}
