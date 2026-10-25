package io.beep.core.domain.billing;

import io.beep.core.domain.DomainException;
import java.util.Locale;

public enum PaymentMethod {
  MTN_MOMO(true),
  ORANGE_MONEY(true),
  CARD(false),
  /** Virement ou espèces, enregistré par le staff : jamais demandé à l'agrégateur. */
  MANUAL(false);

  private final boolean mobileMoney;

  PaymentMethod(boolean mobileMoney) {
    this.mobileMoney = mobileMoney;
  }

  /** Le paiement est confirmé sur le téléphone : un numéro est nécessaire. */
  public boolean mobileMoney() {
    return mobileMoney;
  }

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PaymentMethod parse(String raw) {
    if (raw == null) throw new DomainException("invalid_method");
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_method");
    }
  }
}
