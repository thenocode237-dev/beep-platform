package io.beep.core.domain.billing;

import io.beep.core.domain.DomainException;
import java.util.Locale;

/** Durée d'une période prépayée (specs §6.1 : trimestriel −10 %, annuel −30 %). */
public enum BillingCycle {
  MONTHLY(1),
  QUARTERLY(3),
  ANNUAL(12);

  private final int months;

  BillingCycle(int months) {
    this.months = months;
  }

  public int months() {
    return months;
  }

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static BillingCycle parse(String raw) {
    if (raw == null) throw new DomainException("invalid_cycle");
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_cycle");
    }
  }
}
