package io.beep.core.app.billing;

import io.beep.core.app.port.CouponRepository;
import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.app.port.UserRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.domain.DomainException;
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
import io.beep.core.domain.workspace.Workspace;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Règles communes de la facturation : devis (remise, prorata), effet d'un paiement confirmé sur l'abonnement, le plan du workspace,
 * le coupon et la facture, reçu par e-mail. {@link #apply} s'exécute dans la transaction de l'appelant.
 */
public class BillingEngine {
  /** Résultat d'un paiement appliqué. */
  public record Applied(Payment payment, Invoice invoice, Subscription subscription) {}

  private final SubscriptionRepository subscriptions;
  private final PaymentRepository payments;
  private final InvoiceRepository invoices;
  private final CouponRepository coupons;
  private final PlanRepository plans;
  private final WorkspaceRepository workspaces;
  private final UserRepository users;
  private final MailPort mail;
  private final InvoicePdfPort pdf;
  private final BillingSettings settings;

  public BillingEngine(SubscriptionRepository subscriptions, PaymentRepository payments, InvoiceRepository invoices, CouponRepository coupons, PlanRepository plans,
      WorkspaceRepository workspaces, UserRepository users, MailPort mail, InvoicePdfPort pdf, BillingSettings settings) {
    this.subscriptions = subscriptions;
    this.payments = payments;
    this.invoices = invoices;
    this.coupons = coupons;
    this.plans = plans;
    this.workspaces = workspaces;
    this.users = users;
    this.mail = mail;
    this.pdf = pdf;
    this.settings = settings;
  }

  public BillingSettings settings() {
    return settings;
  }

  public Subscription subscriptionOf(Workspace w, Instant now) {
    return subscriptions.findByWorkspace(w.id()).orElseGet(() -> Subscription.none(w.id(), now));
  }

  /** Pays de facturation (ISO 3166-1 alpha-2) ; un pays sans taux de TVA configuré est refusé avant tout paiement. */
  public String country(String raw) {
    String country = Vat.country(raw == null || raw.isBlank() ? settings.defaultCountry() : raw);
    Vat.rate(settings.vatRatesBp(), country);
    return country;
  }

  /**
   * Devis d'un achat. Règles (specs §6.4) : seuls les plans commercialisés et payants s'achètent ; l'upgrade est immédiat avec le crédit
   * du temps restant ; un plan moins cher s'applique à la fin de la période payée (jamais de remboursement implicite).
   */
  public Quote quote(Workspace w, Plan plan, BillingCycle cycle, String couponCode, Instant now) {
    if (!plan.publicPlan() || plan.free()) throw new DomainException("plan_not_purchasable");
    Subscription sub = subscriptionOf(w, now);
    long credit = 0;
    if (sub.status() == SubscriptionStatus.ACTIVE && now.isBefore(sub.periodEnd()) && !sub.planCode().equals(plan.code())) {
      Plan current = plans.findByCode(sub.planCode()).orElseThrow();
      if (current.priceXaf() > plan.priceXaf()) throw new DomainException("plan_change_at_period_end");
      credit = Pricing.credit(sub.monthlyRateXaf(), sub.periodEnd(), now);
    }
    long list = Pricing.price(plan, cycle);
    long discount = 0;
    String code = null;
    if (couponCode != null && !couponCode.isBlank()) {
      code = Coupon.normalize(couponCode);
      Coupon coupon = coupons.find(code).orElseThrow(() -> new DomainException("invalid_coupon"));
      discount = coupon.discount(list, plan.code(), now);
    }
    credit = Math.min(credit, list - discount);
    long amount = list - discount - credit;
    Instant end = sub.extend(plan.code(), cycle, list - discount, now).periodEnd();
    return new Quote(plan.code(), cycle, list, discount, credit, amount, code, end);
  }

  /**
   * Un paiement confirmé prolonge l'abonnement, fixe le plan du workspace, consomme le coupon et émet la facture. Idempotent :
   * un paiement déjà réglé est retourné tel quel. À appeler dans une transaction, sur un paiement verrouillé.
   */
  public Applied apply(Payment payment, Instant now) {
    if (payment.status() == PaymentStatus.SUCCEEDED) {
      return new Applied(payment, invoices.findByPayment(payment.id()).orElse(null), subscriptions.findByWorkspace(payment.workspaceId()).orElse(null));
    }
    Workspace w = workspaces.findById(payment.workspaceId()).orElseThrow(() -> new DomainException("not_found"));
    Plan plan = plans.findByCode(payment.planCode()).orElseThrow();
    Subscription current = subscriptions.lockByWorkspace(w.id()).orElseGet(() -> Subscription.none(w.id(), now));
    // Tarif mensualisé retenu : celui du prix de liste remisé. Le crédit de prorata ne baisse pas la valeur de la période.
    Subscription next = current.extend(plan.code(), payment.cycle(), payment.listPriceXaf() - payment.discountXaf(), now);
    subscriptions.save(next);
    workspaces.updatePlan(w.id(), plan.code());
    if (payment.couponCode() != null) {
      coupons.lock(payment.couponCode()).ifPresent(c -> coupons.update(
          new Coupon(c.code(), c.percentOff(), c.amountOffXaf(), c.maxRedemptions(), c.redeemed() + 1, c.validUntil(), c.planCodes(), c.active(), c.createdAt())));
    }
    Payment paid = payment.succeeded(now);
    payments.update(paid);
    User owner = users.findById(w.ownerId()).orElseThrow();
    int rate = Vat.rate(settings.vatRatesBp(), paid.country());
    Vat.Split split = Vat.split(paid.amountXaf(), rate);
    Instant periodStart = next.periodEnd().atZone(ZoneOffset.UTC).minusMonths(paid.cycle().months()).toInstant();
    Invoice invoice = invoices.issue(new Invoice("", paid.id(), w.id(), now, w.name(), owner.email().value(), paid.country(), plan.name(), paid.cycle(), periodStart,
        next.periodEnd(), paid.listPriceXaf(), paid.discountXaf(), paid.creditXaf(), paid.amountXaf(), split.net(), split.vat(), rate, paid.method()));
    return new Applied(paid, invoice, next);
  }

  /** Reçu avec la facture en PDF. Hors transaction : un échec d'envoi ne défait jamais un paiement (la facture reste téléchargeable). */
  public boolean sendReceipt(Applied applied) {
    if (applied == null || applied.invoice() == null) return false;
    try {
      Workspace w = workspaces.findById(applied.payment().workspaceId()).orElseThrow();
      User owner = users.findById(w.ownerId()).orElseThrow();
      mail.sendReceipt(owner.email(), owner.locale(), applied.invoice().number(), applied.invoice().planName(), applied.payment().amountXaf(),
          applied.subscription().periodEnd(), pdf.render(applied.invoice()));
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
