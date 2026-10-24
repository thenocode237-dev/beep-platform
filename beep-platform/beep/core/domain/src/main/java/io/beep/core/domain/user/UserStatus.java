package io.beep.core.domain.user;

import java.util.Locale;

public enum UserStatus {
  /** Inscrit, adresse e-mail non confirmée : ne peut pas se connecter. */
  PENDING,
  ACTIVE,
  SUSPENDED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static UserStatus fromDb(String v) {
    return valueOf(v.toUpperCase(Locale.ROOT));
  }
}
