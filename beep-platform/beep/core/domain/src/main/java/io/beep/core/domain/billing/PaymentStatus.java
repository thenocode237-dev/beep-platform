package io.beep.core.domain.billing;

import java.util.Locale;

public enum PaymentStatus {
  PENDING,
  SUCCEEDED,
  FAILED,
  REFUNDED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PaymentStatus parse(String raw) {
    return valueOf(raw.toUpperCase(Locale.ROOT));
  }
}
