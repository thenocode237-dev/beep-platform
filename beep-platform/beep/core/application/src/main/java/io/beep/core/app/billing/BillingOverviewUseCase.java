package io.beep.core.app.billing;

import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Espace client « Abonnement » : état de l'abonnement, plans et prix, paiements, factures, essai Elite. */
public class BillingOverviewUseCase {
  public record Overview(Workspace workspace, Subscription subscription, boolean hasSubscription, Plan plan, List<Plan> plans, List<Payment> payments,
      List<Invoice> invoices, boolean trialAvailable, Instant now) {}

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final PlanRepository plans;
  private final SubscriptionRepository subscriptions;
  private final PaymentRepository payments;
  private final InvoiceRepository invoices;
  private final InvoicePdfPort pdf;
  private final BillingEngine engine;
  private final TransactionPort tx;
  private final Clock clock;

  public BillingOverviewUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, PlanRepository plans, SubscriptionRepository subscriptions,
      PaymentRepository payments, InvoiceRepository invoices, InvoicePdfPort pdf, BillingEngine engine, TransactionPort tx, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.payments = payments;
    this.invoices = invoices;
    this.pdf = pdf;
    this.engine = engine;
    this.tx = tx;
    this.clock = clock;
  }

  public Overview execute(User actor) {
    Workspace w = workspaces.ensureFor(actor);
    Instant now = clock.instant();
    var stored = subscriptions.findByWorkspace(w.id());
    Subscription sub = stored.orElseGet(() -> Subscription.none(w.id(), now));
    Plan plan = plans.findByCode(w.planCode()).orElseThrow();
    return new Overview(w, sub, stored.isPresent(), plan, plans.findAll(), payments.forWorkspace(w.id(), 20), invoices.forWorkspace(w.id(), 50), !sub.trialUsed()
        && stored.map(s -> s.status() == io.beep.core.domain.billing.SubscriptionStatus.EXPIRED).orElse(true), now);
  }

  /** Essai gratuit Elite de 14 jours, une fois par workspace, sans paiement. */
  public Subscription startTrial(User actor) {
    Workspace w = workspaces.ensureFor(actor);
    w.requireWritable();
    Instant now = clock.instant();
    return tx.run(() -> {
      workspaceRepository.lock(w.id());
      Subscription trial = subscriptions.lockByWorkspace(w.id()).orElseGet(() -> Subscription.none(w.id(), now)).startTrial(now);
      subscriptions.save(trial);
      workspaceRepository.updatePlan(w.id(), trial.planCode());
      return trial;
    });
  }

  /** PDF d'une facture du client connecté. */
  public byte[] invoicePdf(User actor, String number) {
    Workspace w = workspaces.ensureFor(actor);
    Invoice invoice = invoices.findByNumber(number).filter(i -> i.workspaceId().equals(w.id())).orElseThrow(() -> new DomainException("not_found"));
    return pdf.render(invoice);
  }

  public BillingEngine engine() {
    return engine;
  }
}
