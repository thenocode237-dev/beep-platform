package io.beep.bo.app.billing;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue « Facturation » : lecture des abonnements, paiements, échéances et factures ; paiements manuels, changement de plan manuel et
 * coupons (droit BILLING_WRITE, ré-authentification TOTP, motif ou référence obligatoire, tout journalisé).
 */
public class BillingUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public BillingUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public CoreGatewayPort.BillingSummary summary(StaffUser actor) {
    actor.require(Permission.BILLING_READ);
    return core.billingSummary(actor.id());
  }

  public List<CoreGatewayPort.SubscriptionView> subscriptions(StaffUser actor, String status, int limit, int offset) {
    actor.require(Permission.BILLING_READ);
    return core.subscriptions(status, limit, offset, actor.id());
  }

  public List<CoreGatewayPort.SubscriptionView> upcoming(StaffUser actor, int days, int limit) {
    actor.require(Permission.BILLING_READ);
    return core.upcomingRenewals(days, limit, actor.id());
  }

  public CoreGatewayPort.PaymentsPage payments(StaffUser actor, String status, UUID workspaceId, int limit, String cursor) {
    actor.require(Permission.BILLING_READ);
    return core.payments(status, workspaceId, limit, cursor, actor.id());
  }

  public List<CoreGatewayPort.InvoiceView> invoices(StaffUser actor, UUID workspaceId) {
    actor.require(Permission.BILLING_READ);
    return core.workspaceInvoices(workspaceId, actor.id());
  }

  /** Une facture contient des données personnelles : son téléchargement est journalisé. */
  public byte[] invoicePdf(StaffUser actor, String number, String ip) {
    actor.require(Permission.BILLING_READ);
    byte[] pdf = core.invoicePdf(number, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "billing.invoice_download", "invoice:" + number, null, ip));
    return pdf;
  }

  public CoreGatewayPort.ManualPaymentResult recordManualPayment(StaffUser actor, UUID workspaceId, String plan, String cycle, long amountXaf, String reference,
      String country, String totp, String ip) {
    actor.require(Permission.BILLING_WRITE);
    if (reference == null || reference.strip().length() < 3) throw new DomainException("invalid_reason");
    stepUp.require(actor, totp);
    var result = core.recordManualPayment(workspaceId, plan, cycle, amountXaf, reference.strip(), country, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "billing.manual_payment", "workspace:" + workspaceId,
        "plan=" + plan + " cycle=" + cycle + " montant=" + amountXaf + " facture=" + (result.invoice() == null ? "-" : result.invoice().number()) + " référence="
            + reference.strip(), ip));
    return result;
  }

  public CoreGatewayPort.SubscriptionView changePlan(StaffUser actor, UUID workspaceId, String plan, Instant until, String reason, String totp, String ip) {
    actor.require(Permission.BILLING_WRITE);
    if (reason == null || reason.strip().length() < 3) throw new DomainException("invalid_reason");
    stepUp.require(actor, totp);
    var result = core.changePlan(workspaceId, plan, until, reason.strip(), actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "billing.plan_change", "workspace:" + workspaceId,
        "plan=" + plan + " jusqu'au=" + (until == null ? "-" : until) + " motif=" + reason.strip(), ip));
    return result;
  }

  public List<CoreGatewayPort.CouponView> coupons(StaffUser actor) {
    actor.require(Permission.BILLING_READ);
    return core.coupons(actor.id());
  }

  public CoreGatewayPort.CouponView createCoupon(StaffUser actor, String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, Instant validUntil,
      List<String> plans, String totp, String ip) {
    actor.require(Permission.BILLING_WRITE);
    stepUp.require(actor, totp);
    var coupon = core.createCoupon(code, percentOff, amountOffXaf, maxRedemptions, validUntil, plans == null ? List.of() : plans, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "billing.coupon_create", "coupon:" + coupon.code(),
        "remise=" + (coupon.percentOff() > 0 ? coupon.percentOff() + "%" : coupon.amountOffXaf() + " FCFA") + " max=" + coupon.maxRedemptions(), ip));
    return coupon;
  }

  public CoreGatewayPort.CouponView setCouponActive(StaffUser actor, String code, boolean active, String totp, String ip) {
    actor.require(Permission.BILLING_WRITE);
    stepUp.require(actor, totp);
    var coupon = core.setCouponActive(code, active, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), active ? "billing.coupon_enable" : "billing.coupon_disable", "coupon:" + coupon.code(), null, ip));
    return coupon;
  }
}
