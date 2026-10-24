package io.beep.core.domain.link;

import java.util.Locale;

public enum LinkStatus {
  ACTIVE,
  /** Désactivé par le staff (modération) : page d'avertissement, aucune redirection. */
  DISABLED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static LinkStatus fromDb(String v) {
    return valueOf(v.toUpperCase(Locale.ROOT));
  }
}
