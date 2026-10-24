package io.beep.core.app.auth;

import java.util.Locale;

final class Locales {
  private Locales() {}

  /** Langues prises en charge : fr (défaut) et en. */
  static String normalize(String raw) {
    return raw != null && raw.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "fr";
  }
}
