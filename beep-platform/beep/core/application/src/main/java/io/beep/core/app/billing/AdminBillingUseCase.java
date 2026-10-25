package io.beep.core.app.billing;

import io.beep.core.app.common.Paging;
import io.beep.core.app.port.CouponRepository;
import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Coupon;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentMethod;
import io.beep.core.domain.billing.PaymentStatus;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Entitlements;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Facturation côté staff : tableau de bord (MRR/ARR, impayés, échéances), paiements manuels, changement de plan, coupons, publication de plans. */
public class AdminBillingUseCase {
  public record Summary(SubscriptionRepository.Summary subscriptions, long arrXaf, long revenue30dXaf, long pendingPayments, long failedPayments) {}

  /** Plan soumis à la publication (brouillon validé dans le back office). Prix trimestriel / annuel `null` = calculés (−10 % / −30 %). */
  public record PlanDraft(String code, String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, boolean publicPlan, Map<String, Long> entitlements) {}

  private static final Pattern PLAN_CODE = Pattern.compile("[a-z][a-z0-9_-]{1,31}");
  private static final Duration MAX_GRANT = Duration.ofDays(5 * 366);

  private final SubscriptionRepository subscriptions;
  private final PaymentRepository payments;
  private final InvoiceRepository invoices;
  private final CouponRepository coupons;
  private final PlanRepository plans;
  private final WorkspaceRepository workspaces;
  private final InvoicePdfPort pdf;
  private final BillingEngine engine;
  private final TransactionPort tx;
  private final Clock clock;

  public AdminBillingUseCase(SubscriptionRepository subscriptions, PaymentRepository payments, InvoiceRepository invoices, CouponRepository coupons, PlanRepository plans,
      WorkspaceRepository workspaces, InvoicePdfPort pdf, BillingEngine engine, TransactionPort tx, Clock clock) {
    this.subscriptions = subscriptions;
    this.payments = payments;
    this.invoices = invoices;
    this.coupons = coupons;
    this.plans = plans;
    this.workspaces = workspaces;
    this.pdf = pdf;
    this.engine = engine;
    this.tx = tx;
    this.clock = clock;
  }

  public Summary summary() {
    var s = subscriptions.summary();
    return new Summary(s, s.mrrXaf() * 12, payments.succeededSince(clock.instant().minus(Duration.ofDays(30))), payments.countByStatus(PaymentStatus.PENDING),
        payments.countByStatus(PaymentStatus.FAILED));
  }

  public List<SubscriptionRepository.Row> subscriptions(String status, int limit, int offset) {
    SubscriptionStatus parsed = null;
    if (status != null && !status.isBlank()) {
      try {
        parsed = SubscriptionStatus.parse(status);
      } catch (IllegalArgumentException e) {
        throw new DomainException("invalid_request");
      }
    }
    return subscriptions.page(parsed, Paging.limit(limit), Math.max(0, offset));
  }

  /** Échéances des `days` prochains jours (périodes payées et essais), les plus proches d'abord. */
  public List<SubscriptionRepository.Row> upcoming(int days, int limit) {
    Instant now = clock.instant();
    return subscriptions.upcoming(now, now.plus(Duration.ofDays(Math.max(1, Math.min(days, 90)))), Paging.limit(limit));
  }

  public PaymentRepository.Page payments(String status, UUID workspaceId, int limit, String cursor) {
    PaymentStatus parsed = null;
    if (status != null && !status.isBlank()) {
      try {
        parsed = PaymentStatus.parse(status);
      } catch (IllegalArgumentException e) {
        throw new DomainException("invalid_request");
      }
    }
    return payments.page(parsed, workspaceId, Paging.limit(limit), cursor);
  }

  public List<Invoice> invoices(UUID workspaceId) {
    workspaces.findById(workspaceId).orElseThrow(() -> new DomainException("not_found"));
    return invoices.forWorkspace(workspaceId, 100);
  }

  public byte[] invoicePdf(String number) {
    return pdf.render(invoices.findByNumber(number).orElseThrow(() -> new DomainException("not_found")));
  }

  /**
   * Paiement hors ligne (virement, espèces) : prolonge l'abonnement comme un paiement en ligne et émet la facture (specs §6.4 règle 10).
   * Le plan peut ne pas être commercialisé (Elite avant la phase 3) : c'est une décision explicite du staff.
   */
  public BillingEngine.Applied recordManualPayment(UUID workspaceId, String planCode, String cycle, long amountXaf, String reference, String country, String staffId) {
    if (amountXaf < 1 || amountXaf > 100_000_000L) throw new DomainException("invalid_amount");
    String note = Link.reason(reference);
    BillingCycle billingCycle = BillingCycle.parse(cycle);
    Workspace w = workspaces.findById(workspaceId).orElseThrow(() -> new DomainException("not_found"));
    Plan plan = plans.findByCode(planCode == null ? "" : planCode).orElseThrow(() -> new DomainException("not_found"));
    if (plan.free()) throw new DomainException("plan_not_purchasable");
    String billingCountry = engine.country(country);
    Instant now = clock.instant();
    UUID id = UUID.randomUUID();
    var applied = tx.run(() -> {
      workspaces.lock(w.id());
      Payment p = new Payment(id, w.id(), plan.code(), billingCycle, PaymentMethod.MANUAL, "manual", "manual-" + id, PaymentStatus.PENDING, amountXaf, 0, 0, amountXaf, null,
          billingCountry, null, note, staffId, null, now, null);
      payments.insert(p);
      return engine.apply(payments.lockById(id).orElseThrow(), now);
    });
    engine.sendReceipt(applied);
    return applied;
  }

  /**
   * Changement de plan manuel : période offerte jusqu'à `until` (sans facture), ou retour au plan Starter (`starter`, `until` ignoré).
   * Les droits spécifiques restent gérés par les overrides.
   */
  public Subscription changePlan(UUID workspaceId, String planCode, Instant until, String reason) {
    Link.reason(reason);
    Workspace w = workspaces.findById(workspaceId).orElseThrow(() -> new DomainException("not_found"));
    Plan plan = plans.findByCode(planCode == null ? "" : planCode).orElseThrow(() -> new DomainException("not_found"));
    Instant now = clock.instant();
    return tx.run(() -> {
      workspaces.lock(w.id());
      Subscription current = subscriptions.lockByWorkspace(w.id()).orElseGet(() -> Subscription.none(w.id(), now));
      Subscription next;
      if (plan.free()) {
        next = current.withStatus(SubscriptionStatus.EXPIRED, now).withNotice(Subscription.NOTICE_EXPIRED, now);
      } else {
        if (until == null || !until.isAfter(now) || until.isAfter(now.plus(MAX_GRANT))) throw new DomainException("invalid_request");
        next = current.grant(plan.code(), until, now);
      }
      subscriptions.save(next);
      workspaces.updatePlan(w.id(), plan.free() ? Workspace.DEFAULT_PLAN : plan.code());
      return next;
    });
  }

  // ---- Coupons ----
  public List<Coupon> coupons() {
    return coupons.list(200);
  }

  public Coupon createCoupon(String code, int percentOff, long amountOffXaf, Integer maxRedemptions, Instant validUntil, Set<String> planCodes) {
    Instant now = clock.instant();
    if (validUntil != null && !validUntil.isAfter(now)) throw new DomainException("invalid_coupon");
    Set<String> codes = planCodes == null ? Set.of() : planCodes;
    for (String c : codes) if (plans.findByCode(c).isEmpty()) throw new DomainException("invalid_coupon");
    Coupon coupon = Coupon.create(code, percentOff, amountOffXaf, maxRedemptions, validUntil, codes, now);
    try {
      coupons.insert(coupon);
    } catch (CouponRepository.DuplicateCodeException e) {
      throw new DomainException("coupon_exists");
    }
    return coupon;
  }

  public Coupon setCouponActive(String code, boolean active) {
    return tx.run(() -> {
      Coupon c = coupons.lock(Coupon.normalize(code)).orElseThrow(() -> new DomainException("not_found"));
      Coupon next = new Coupon(c.code(), c.percentOff(), c.amountOffXaf(), c.maxRedemptions(), c.redeemed(), c.validUntil(), c.planCodes(), active, c.createdAt());
      coupons.update(next);
      return next;
    });
  }

  // ---- Plans : publication d'un brouillon du back office (versionnement ; le core garde la copie lue à l'exécution) ----
  public Plan publishPlan(PlanDraft d, String staffId) {
    if (d.code() == null || !PLAN_CODE.matcher(d.code()).matches()) throw new DomainException("invalid_plan");
    if (d.name() == null || d.name().isBlank() || d.name().length() > 60) throw new DomainException("invalid_plan");
    long month = d.priceXaf();
    if (month < 0 || month > 10_000_000L || (d.priceQuarterlyXaf() != null && d.priceQuarterlyXaf() < 0) || (d.priceAnnualXaf() != null && d.priceAnnualXaf() < 0)) {
      throw new DomainException("invalid_plan");
    }
    if (d.entitlements() == null) throw new DomainException("invalid_plan");
    for (var e : d.entitlements().entrySet()) {
      if (e.getValue() == null) throw new DomainException("invalid_plan");
      Entitlements.validateOverride(e.getKey(), e.getValue());
    }
    var existing = plans.findByCode(d.code());
    if (d.code().equals(Workspace.DEFAULT_PLAN) && (month != 0 || !d.publicPlan())) throw new DomainException("invalid_plan"); // Starter reste gratuit et ouvert
    int version = existing.map(Plan::version).orElse(0) + 1;
    Plan plan = d.priceQuarterlyXaf() == null && d.priceAnnualXaf() == null
        ? new Plan(d.code(), d.name().strip(), version, month, d.publicPlan(), d.entitlements())
        : new Plan(d.code(), d.name().strip(), version, month, d.priceQuarterlyXaf() == null ? io.beep.core.domain.billing.Pricing.quarterly(month) : d.priceQuarterlyXaf(),
            d.priceAnnualXaf() == null ? io.beep.core.domain.billing.Pricing.annual(month) : d.priceAnnualXaf(), d.publicPlan(), d.entitlements());
    tx.run(() -> plans.publish(plan, staffId, clock.instant()));
    return plan;
  }
}
