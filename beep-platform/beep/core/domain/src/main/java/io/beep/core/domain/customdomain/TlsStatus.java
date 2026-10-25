package io.beep.core.domain.customdomain;

import java.util.Locale;

/** Certificat à la demande (Caddy) : `ACTIVE` dès que Caddy a demandé l'autorisation d'émettre pour ce nom, c'est-à-dire à la première visite HTTPS. */
public enum TlsStatus {
  PENDING,
  ACTIVE;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static TlsStatus parse(String raw) {
    return valueOf(raw.toUpperCase(Locale.ROOT));
  }
}
