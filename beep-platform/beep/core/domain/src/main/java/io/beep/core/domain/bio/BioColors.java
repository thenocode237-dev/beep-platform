package io.beep.core.domain.bio;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Couleurs personnalisées d'une page bio (hexadécimal #RRGGBB, chacune facultative). Principale : boutons ; secondaire : fond de page ; accent : contours,
 * icônes et focus. Les couleurs de texte sont dérivées au rendu pour garantir le contraste.
 */
public record BioColors(String primary, String secondary, String accent) {
  public static final BioColors NONE = new BioColors(null, null, null);
  private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  public static BioColors of(String primary, String secondary, String accent) {
    return new BioColors(hex(primary), hex(secondary), hex(accent));
  }

  static String hex(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String v = raw.strip();
    if (!HEX.matcher(v).matches()) throw new DomainException("invalid_color");
    return v.toUpperCase(Locale.ROOT);
  }
}
