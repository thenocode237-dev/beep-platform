package io.beep.core.app.billing;

import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tâche du worker : applique le cycle de vie des abonnements prépayés (specs §6.4) et envoie les rappels.
 *
 *  - rappels d'échéance J−7, J−3, J−1 (une fois chacun par période) ;
 *  - échéance dépassée → période de grâce de 7 jours, accès complet ;
 *  - fin de grâce (ou fin d'essai) → retour au plan Starter ; les ressources sont conservées et continuent de rediriger.
 *
 * Un e-mail qui échoue n'est pas marqué envoyé : il est retenté à la prochaine exécution.
 */
public class BillingLifecycleUseCase {
  public record Report(int reminders, int graced, int expired, int mailFailures) {
    public String detail() {
      return "rappels=" + reminders + " grâce=" + graced + " expirés=" + expired + " échecs-mail=" + mailFailures;
    }
  }

  private static final Duration LOOKAHEAD = Duration.ofDays(8);
  private static final int BATCH = 500;

  private final SubscriptionRepository subscriptions;
  private final WorkspaceRepository workspaces;
  private final PlanRepository plans;
  private final UserRepository users;
  private final PaymentRepository payments;
  private final MailPort mail;
  private final TransactionPort tx;
  private final Clock clock;

  public BillingLifecycleUseCase(SubscriptionRepository subscriptions, WorkspaceRepository workspaces, PlanRepository plans, UserRepository users, PaymentRepository payments,
      MailPort mail, TransactionPort tx, Clock clock) {
    this.subscriptions = subscriptions;
    this.workspaces = workspaces;
    this.plans = plans;
    this.users = users;
    this.payments = payments;
    this.mail = mail;
    this.tx = tx;
    this.clock = clock;
  }

  public Report run() {
    Instant now = clock.instant();
    int reminders = 0;
    int graced = 0;
    int expired = 0;
    int failures = 0;
    List<Subscription> candidates = subscriptions.candidates(now.plus(LOOKAHEAD), BATCH);
    for (Subscription candidate : candidates) {
      Outcome o = tx.run(() -> process(candidate.workspaceId(), now));
      reminders += o.reminders;
      graced += o.graced;
      expired += o.expired;
      failures += o.failures;
    }
    return new Report(reminders, graced, expired, failures);
  }

  private static final class Outcome {
    int reminders;
    int graced;
    int expired;
    int failures;
  }

  private Outcome process(java.util.UUID workspaceId, Instant now) {
    Outcome o = new Outcome();
    Optional<Subscription> locked = subscriptions.lockByWorkspace(workspaceId);
    if (locked.isEmpty()) return o;
    Subscription sub = locked.get();
    Optional<Workspace> w = workspaces.findById(workspaceId);
    if (w.isEmpty()) return o;
    Optional<User> owner = users.findById(w.get().ownerId());
    String planName = plans.findByCode(sub.planCode()).map(p -> p.name()).orElse(sub.planCode());

    SubscriptionStatus due = sub.dueStatus(now);
    if (due != sub.status()) {
      sub = sub.withStatus(due, now);
      if (due == SubscriptionStatus.EXPIRED) {
        workspaces.updatePlan(workspaceId, io.beep.core.domain.workspace.Workspace.DEFAULT_PLAN);
        o.expired++;
      } else {
        o.graced++;
      }
      subscriptions.save(sub);
    }

    // Courriels : un par événement et par période, marqué seulement une fois envoyé.
    if (owner.isPresent()) {
      User u = owner.get();
      try {
        switch (sub.status()) {
          case GRACE -> {
            if (!sub.notices().contains(Subscription.NOTICE_GRACE)) {
              mail.sendGraceNotice(u.email(), u.locale(), planName, sub.graceEnd());
              subscriptions.save(sub.withNotice(Subscription.NOTICE_GRACE, now));
            }
          }
          case EXPIRED -> {
            if (sub.periodEnd().isAfter(sub.createdAt()) && !sub.notices().contains(Subscription.NOTICE_EXPIRED)) {
              mail.sendPlanExpired(u.email(), u.locale(), planName);
              subscriptions.save(sub.withNotice(Subscription.NOTICE_EXPIRED, now));
            }
          }
          case ACTIVE, TRIALING -> {
            String notice = reminderFor(sub, now);
            if (notice != null && !sub.notices().contains(notice)) {
              int days = switch (notice) {
                case Subscription.NOTICE_D1 -> 1;
                case Subscription.NOTICE_D3 -> 3;
                default -> 7;
              };
              mail.sendRenewalReminder(u.email(), u.locale(), planName, sub.status() == SubscriptionStatus.TRIALING, days, sub.periodEnd());
              // Un rappel plus proche couvre les précédents : pas de J−7 envoyé après un J−3 manqué.
              Subscription marked = sub.withNotice(notice, now);
              if (days == 1) marked = marked.withNotice(Subscription.NOTICE_D3, now).withNotice(Subscription.NOTICE_D7, now);
              if (days == 3) marked = marked.withNotice(Subscription.NOTICE_D7, now);
              subscriptions.save(marked);
              o.reminders++;
            }
          }
        }
      } catch (RuntimeException e) {
        o.failures++;
      }
    }
    return o;
  }

  private static String reminderFor(Subscription sub, Instant now) {
    Duration left = Duration.between(now, sub.periodEnd());
    if (left.isNegative() || left.isZero()) return null;
    if (left.compareTo(Duration.ofDays(1)) <= 0) return Subscription.NOTICE_D1;
    if (left.compareTo(Duration.ofDays(3)) <= 0) return Subscription.NOTICE_D3;
    if (left.compareTo(Duration.ofDays(7)) <= 0) return Subscription.NOTICE_D7;
    return null;
  }

  /** Rapproche les paiements restés en attente (notification perdue, client parti avant de confirmer). */
  public List<java.util.UUID> pendingToReconcile(Duration olderThan, int limit) {
    return payments.pendingBefore(clock.instant().minus(olderThan), limit).stream().map(p -> p.id()).toList();
  }
}
