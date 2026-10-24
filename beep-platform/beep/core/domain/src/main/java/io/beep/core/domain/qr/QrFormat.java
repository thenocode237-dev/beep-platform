package io.beep.core.domain.qr;

import io.beep.core.domain.DomainException;
import java.util.Locale;

/** Formats d'export prêts à imprimer. */
public enum QrFormat {
  SVG("image/svg+xml", "svg"),
  PNG("image/png", "png"),
  PDF("application/pdf", "pdf");

  private final String mediaType;
  private final String extension;

  QrFormat(String mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  public String mediaType() {
    return mediaType;
  }

  public String extension() {
    return extension;
  }

  public static QrFormat parse(String raw) {
    try {
      return valueOf((raw == null ? "svg" : raw).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_format");
    }
  }
}
