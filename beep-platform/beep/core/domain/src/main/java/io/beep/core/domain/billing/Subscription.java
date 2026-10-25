package io.beep.core.domain.billing;

import io.beep.core.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Abonnement prépayé d'un workspace. Le plan du workspace ne change qu'aux transitions (paiement, fin de grâce, fin d'essai) ;
 * les redirections ne dépendent jamais de l'état (specs §6.4 règle 2).
 *
 * @param cycle          null pendant l'essai
 * @param monthlyRateXaf équivalent mensuel de la dernière période payée (0 pour un essai ou une période offerte) : base du MRR et du prorata
 * @param notices        rappels déjà envoyés pour la période en cours (d7, d3, d1, grace, expired)
 * @param trialUsed      l'essai Elite n'est accordé qu'une fois par workspace
 */
public record Subscription(UUID id, UUID workspaceId, String planCode, BillingCycle cycle, SubscriptionStatus status, Instant periodStart, Instant periodEnd,
    long monthlyRateXaf, Set<String> notices, boolean trialUsed, Instant createdAt, Instant updatedAt) {
  public static final int TRIAL_DAYS = 14;
  public static final Duration GRACE = Duration.ofDays(7);
  public static final String TRIAL_PLAN = "elite";
  /** Les liens en excès sont mis en pause 30 jours après la fin de la période (specs §6.4). */
  public static final Duration PAUSE_AFTER = Duration.ofDays(30);

  public static final String NOTICE_D7 = "d7";
  public static final String NOTICE_D3 = "d3";
  public static final String NOTICE_D1 = "d1";
  public static final String NOTICE_GRACE = "grace";
  public static final String NOTICE_EXPIRED = "expired";

  public Subscription {
    notices = Set.copyOf(notices);
  }

  /** Workspace sans historique d'abonnement : état « expiré » sur le plan Starter, essai encore disponible. */
  public static Subscription none(UUID workspaceId, Instant now) {
    return new Subscription(UUID.randomUUID(), workspaceId, "starter", null, SubscriptionStatus.EXPIRED, now, now, 0, Set.of(), false, now, now);
  }

  public Instant graceEnd() {
    return periodEnd.plus(GRACE);
  }

  /** Fin de la période + 30 jours : seuil de mise en pause des liens en excès. */
  public Instant pauseAt() {
    return periodEnd.plus(PAUSE_AFTER);
  }

  /** Démarre l'essai Elite de 14 jours, une seule fois par workspace et jamais par-dessus une période payée. */
  public Subscription startTrial(Instant now) {
    if (trialUsed) throw new DomainException("trial_already_used");
    if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.GRACE || status == SubscriptionStatus.TRIALING) throw new DomainException("subscription_active");
    return new Subscription(id, workspaceId, TRIAL_PLAN, null, SubscriptionStatus.TRIALING, now, now.plus(Duration.ofDays(TRIAL_DAYS)), 0, Set.of(), true, createdAt, now);
  }

  /**
   * Applique un paiement (ou une période offerte) : même plan et période encore valable → la nouvelle période s'ajoute à la fin
   * de la précédente (le renouvellement anticipé ne fait rien perdre) ; sinon (changement de plan, essai, grâce, expiré)
   * elle démarre maintenant — l'upgrade a déjà déduit le crédit du temps restant du prix demandé.
   */
  public Subscription extend(String newPlanCode, BillingCycle newCycle, long paidXaf, Instant now) {
    boolean stack = newPlanCode.equals(planCode) && status == SubscriptionStatus.ACTIVE && now.isBefore(periodEnd);
    Instant start = stack ? periodEnd : now;
    Instant end = start.atZone(ZoneOffset.UTC).plusMonths(newCycle.months()).toInstant();
    return new Subscription(id, workspaceId, newPlanCode, newCycle, SubscriptionStatus.ACTIVE, stack ? periodStart : now, end,
        Pricing.monthlyEquivalent(paidXaf, newCycle), Set.of(), trialUsed, createdAt, now);
  }

  /** Période offerte par le staff (changement de plan manuel) : aucun montant, aucune récurrence. */
  public Subscription grant(String newPlanCode, Instant until, Instant now) {
    return new Subscription(id, workspaceId, newPlanCode, null, SubscriptionStatus.ACTIVE, now, until, 0, Set.of(), trialUsed, createdAt, now);
  }

  /** Statut dû à l'instant donné (les transitions sont appliquées par le worker, jamais à la lecture). */
  public SubscriptionStatus dueStatus(Instant now) {
    return switch (status) {
      case TRIALING -> now.isBefore(periodEnd) ? SubscriptionStatus.TRIALING : SubscriptionStatus.EXPIRED;
      case ACTIVE, GRACE -> now.isBefore(periodEnd) ? SubscriptionStatus.ACTIVE : now.isBefore(graceEnd()) ? SubscriptionStatus.GRACE : SubscriptionStatus.EXPIRED;
      case EXPIRED -> SubscriptionStatus.EXPIRED;
    };
  }

  public Subscription withStatus(SubscriptionStatus next, Instant now) {
    return new Subscription(id, workspaceId, planCode, cycle, next, periodStart, periodEnd, monthlyRateXaf, notices, trialUsed, createdAt, now);
  }

  public Subscription withNotice(String notice, Instant now) {
    Set<String> all = new HashSet<>(notices);
    all.add(notice);
    return new Subscription(id, workspaceId, planCode, cycle, status, periodStart, periodEnd, monthlyRateXaf, all, trialUsed, createdAt, now);
  }

  /** MRR : équivalent mensuel d'une période payée en cours ; 0 pour un essai, la grâce et l'expiré. */
  public long mrrXaf() {
    return status == SubscriptionStatus.ACTIVE ? monthlyRateXaf : 0;
  }
}
