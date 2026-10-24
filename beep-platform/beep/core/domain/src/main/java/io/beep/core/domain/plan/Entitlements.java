package io.beep.core.domain.plan;

import io.beep.core.domain.DomainException;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

/** Droits effectifs d'un workspace : ceux du plan, remplacés clé par clé par les overrides du staff. */
public record Entitlements(Map<String, Long> values) {
  public static final long UNLIMITED = -1;

  public Entitlements {
    values = Map.copyOf(values);
  }

  public static Entitlements of(Plan plan, Map<String, Long> overrides) {
    Map<String, Long> merged = new TreeMap<>(plan.entitlements());
    merged.putAll(overrides);
    return new Entitlements(merged);
  }

  public OptionalLong value(String key) {
    Long v = values.get(key);
    return v == null ? OptionalLong.empty() : OptionalLong.of(v);
  }

  public boolean enabled(String feature) {
    return value(feature).orElse(0) > 0;
  }

  /** Fenêtre d'analytics visible (jours) ; -1 = illimitée dans la limite des 13 mois d'événements bruts. */
  public long retentionDays() {
    return value(EntitlementKeys.ANALYTICS_RETENTION_DAYS).orElse(30);
  }

  /** Validation d'un override saisi par le staff (clé connue, valeur cohérente avec son type). */
  public static void validateOverride(String key, Long value) {
    if (!EntitlementKeys.ALL.contains(key)) throw new DomainException("unknown_entitlement");
    if (value == null) return;
    if (EntitlementKeys.isFeature(key) ? value != 0 && value != 1 : value < UNLIMITED || value > 1_000_000) throw new DomainException("invalid_entitlement");
  }
}
