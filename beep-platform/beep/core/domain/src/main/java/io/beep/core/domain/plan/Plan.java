package io.beep.core.domain.plan;

import io.beep.core.domain.billing.Pricing;
import java.util.Map;

/**
 * Plan publié (copie lue à l'exécution). Brouillons et versionnement éditorial vivent dans le back office, qui publie vers le core ;
 * `publicPlan` = commercialisé, sinon affiché « bientôt ». Prix TTC en FCFA ; trimestre et année par défaut calculés (−10 % / −30 %).
 */
public record Plan(String code, String name, int version, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf, boolean publicPlan,
    Map<String, Long> entitlements) {
  public Plan {
    entitlements = Map.copyOf(entitlements);
  }

  public Plan(String code, String name, int version, long priceXaf, boolean publicPlan, Map<String, Long> entitlements) {
    this(code, name, version, priceXaf, Pricing.quarterly(priceXaf), Pricing.annual(priceXaf), publicPlan, entitlements);
  }

  public boolean free() {
    return priceXaf == 0;
  }
}
