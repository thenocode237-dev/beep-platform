package io.beep.core.domain.billing;

import java.util.Locale;

/**
 * Cycle de vie d'un abonnement prépayé (specs §6.4) :
 * essai ou période payée → période de grâce de 7 jours (accès complet) → expiré (retour au plan Starter).
 */
public enum SubscriptionStatus {
  TRIALING,
  ACTIVE,
  GRACE,
  EXPIRED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static SubscriptionStatus parse(String raw) {
    return valueOf(raw.toUpperCase(Locale.ROOT));
  }
}
