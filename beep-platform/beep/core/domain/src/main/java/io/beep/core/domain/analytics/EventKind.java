package io.beep.core.domain.analytics;

import java.util.Locale;

/** Nature d'un événement : clic sur un lien, scan d'un QR dynamique, vue d'une page bio, clic sur un bloc de page bio. */
public enum EventKind {
  CLICK,
  SCAN,
  VIEW,
  BIO_CLICK;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }
}
