package io.beep.core.app.billing;

import io.beep.core.app.port.PaymentGatewayPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentMethod;
import io.beep.core.domain.billing.PaymentStatus;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Achat ou renouvellement d'une période prépayée : devis, création du paiement, demande à l'agrégateur (push Mobile Money ou page carte). */
public class CheckoutUseCase {
  public record Command(String plan, String cycle, String method, String phone, String coupon, String country) {}

  /** `redirectUrl` : page de paiement hébergée (carte) ; `invoice` renseignée si la période est gratuite (code promo à 100 %). */
  public record Result(Payment payment, Quote quote, String redirectUrl, Invoice invoice) {}

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final PlanRepository plans;
  private final PaymentRepository payments;
  private final PaymentGatewayPort gateway;
  private final BillingEngine engine;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final Clock clock;

  public CheckoutUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, PlanRepository plans, PaymentRepository payments, PaymentGatewayPort gateway,
      BillingEngine engine, RateLimiterPort limiter, TransactionPort tx, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.plans = plans;
    this.payments = payments;
    this.gateway = gateway;
    this.engine = engine;
    this.limiter = limiter;
    this.tx = tx;
    this.clock = clock;
  }

  /** Devis sans effet de bord (affiché avant la confirmation). */
  public Quote quote(User actor, Command c) {
    Workspace w = workspaces.ensureFor(actor);
    return engine.quote(w, plan(c.plan()), BillingCycle.parse(c.cycle()), c.coupon(), clock.instant());
  }

  public Result start(User actor, Command c) {
    Workspace w = workspaces.ensureFor(actor);
    w.requireWritable();
    if (!limiter.tryAcquire("checkout:" + actor.id(), 10, Duration.ofMinutes(10))) throw new DomainException("rate_limited");
    Plan plan = plan(c.plan());
    BillingCycle cycle = BillingCycle.parse(c.cycle());
    PaymentMethod method = PaymentMethod.parse(c.method());
    if (method == PaymentMethod.MANUAL) throw new DomainException("invalid_method");
    String country = engine.country(c.country());
    String phone = method.mobileMoney() ? phone(c.phone()) : null;
    Instant now = clock.instant();

    Payment pending = tx.run(() -> {
      workspaceRepository.lock(w.id()); // un seul achat à la fois par workspace : le devis (crédit, empilement) reste exact
      Quote q = engine.quote(w, plan, cycle, c.coupon(), now);
      UUID id = UUID.randomUUID();
      boolean free = q.amountXaf() == 0;
      Payment p = new Payment(id, w.id(), plan.code(), cycle, method, free ? "internal" : gateway.name(), (free ? "internal-" : "pending-") + id, PaymentStatus.PENDING,
          q.listPriceXaf(), q.discountXaf(), q.creditXaf(), q.amountXaf(), q.couponCode(), country, phone == null ? null : phone.substring(phone.length() - 4), null, null,
          null, now, null);
      payments.insert(p);
      return p;
    });

    if (pending.amountXaf() == 0) { // période entièrement couverte par un code promo ou le crédit : aucun appel à l'agrégateur
      BillingEngine.Applied applied = tx.run(() -> engine.apply(payments.lockById(pending.id()).orElseThrow(), clock.instant()));
      engine.sendReceipt(applied);
      return new Result(applied.payment(), null, null, applied.invoice());
    }

    PaymentGatewayPort.Charge charge;
    try {
      charge = gateway.initiate(new PaymentGatewayPort.ChargeRequest("beep-" + pending.id(), pending.amountXaf(), method, phone, actor.email().value(),
          "Beep " + plan.name(), engine.settings().callbackUrl()));
    } catch (PaymentGatewayPort.GatewayException e) {
      payments.update(pending.failed("gateway_unavailable"));
      throw new DomainException("payment_unavailable");
    }
    Payment saved = pending.withProviderRef(charge.providerRef());
    payments.update(saved);
    return new Result(saved, null, charge.redirectUrl(), null);
  }

  private Plan plan(String code) {
    if (code == null) throw new DomainException("invalid_request");
    return plans.findByCode(code.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new DomainException("not_found"));
  }

  /** Numéro Mobile Money : chiffres seuls (indicatif compris), 8 à 15 chiffres. */
  static String phone(String raw) {
    String digits = raw == null ? "" : raw.replaceAll("[\\s.+-]", "");
    if (!digits.matches("\\d{8,15}")) throw new DomainException("invalid_phone");
    return digits;
  }
}
