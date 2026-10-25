package io.beep.core.app.billing;

import java.time.Duration;
import java.util.Map;

/**
 * Réglages de facturation. Les taux de TVA (points de base par pays) sont configurés, pas codés : ils doivent être validés
 * avec un expert-comptable avant la production.
 */
public record BillingSettings(Map<String, Integer> vatRatesBp, String defaultCountry, Duration pendingTimeout, String callbackUrl) {
  public BillingSettings {
    vatRatesBp = Map.copyOf(vatRatesBp);
  }

  public static BillingSettings defaults() {
    return new BillingSettings(Map.of("CM", 1925, "GA", 1800), "CM", Duration.ofMinutes(30), "");
  }
}
