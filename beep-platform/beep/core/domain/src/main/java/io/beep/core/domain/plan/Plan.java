package io.beep.core.domain.plan;

import java.util.Map;

/**
 * Plan publié (copie lue à l'exécution). Les brouillons et le versionnement éditorial vivent dans le back office
 * (phase 2) ; `publicPlan` = commercialisé, sinon affiché « bientôt ».
 */
public record Plan(String code, String name, int version, long priceXaf, boolean publicPlan, Map<String, Long> entitlements) {
  public Plan {
    entitlements = Map.copyOf(entitlements);
  }
}
