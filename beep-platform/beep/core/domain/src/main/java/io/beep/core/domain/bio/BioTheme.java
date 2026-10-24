package io.beep.core.domain.bio;

import io.beep.core.domain.DomainException;
import java.util.Locale;

/** Thèmes de base des pages bio (palettes issues de la charte Atelier ; contrastes AA vérifiés au rendu). */
public enum BioTheme {
  IVOIRE,
  FORET,
  NUIT,
  SOLEIL;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static BioTheme parse(String raw) {
    if (raw == null || raw.isBlank()) return IVOIRE;
    try {
      return valueOf(raw.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_theme");
    }
  }
}
