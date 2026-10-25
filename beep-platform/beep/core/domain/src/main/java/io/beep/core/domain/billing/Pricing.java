package io.beep.core.domain.billing;

import io.beep.core.domain.plan.Plan;
import java.time.Duration;
import java.time.Instant;

/** Prix d'un plan par période et prorata. Les prix sont TTC (hypothèse H6) et en FCFA, sans décimales. */
public final class Pricing {
  private Pricing() {}

  /** Durée moyenne d'un mois, pour valoriser le temps restant d'une période. */
  private static final double SECONDS_PER_MONTH = 365.25 / 12 * 86_400;

  /** Trimestre : −10 % sur trois mois, arrondi à 10 FCFA (4 999 → 13 500). */
  public static long quarterly(long monthly) {
    return roundTo10(monthly * 3 * 0.90);
  }

  /** Année : −30 % sur douze mois, arrondi à 10 FCFA (4 999 → 41 990). */
  public static long annual(long monthly) {
    return roundTo10(monthly * 12 * 0.70);
  }

  public static long price(Plan plan, BillingCycle cycle) {
    return switch (cycle) {
      case MONTHLY -> plan.priceXaf();
      case QUARTERLY -> plan.priceQuarterlyXaf();
      case ANNUAL -> plan.priceAnnualXaf();
    };
  }

  /** Équivalent mensuel d'un montant payé pour une période (base du MRR et du prorata). */
  public static long monthlyEquivalent(long amount, BillingCycle cycle) {
    return Math.round((double) amount / cycle.months());
  }

  /**
   * Crédit de l'upgrade immédiat (specs §6.4 règle 4) : valeur du temps restant de la période déjà payée,
   * au tarif mensualisé réellement payé. Nul si la période est terminée.
   */
  public static long credit(long monthlyRateXaf, Instant periodEnd, Instant now) {
    if (monthlyRateXaf <= 0 || !now.isBefore(periodEnd)) return 0;
    return Math.round(monthlyRateXaf * Duration.between(now, periodEnd).toSeconds() / SECONDS_PER_MONTH);
  }

  private static long roundTo10(double v) {
    return Math.round(v / 10.0) * 10L;
  }
}
