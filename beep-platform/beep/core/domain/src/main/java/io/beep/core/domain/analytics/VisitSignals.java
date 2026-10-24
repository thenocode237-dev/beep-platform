package io.beep.core.domain.analytics;

import java.net.URI;
import java.util.Locale;

/** Normalisation des signaux d'une visite : référent réduit à son hôte, langue principale. Aucune donnée brute conservée. */
public final class VisitSignals {
  public static final String DIRECT = "direct";

  private VisitSignals() {}

  /** Hôte du référent (sans « www. »), ou `direct`. Le chemin et la requête ne sont jamais conservés. */
  public static String referrerHost(String referer) {
    if (referer == null || referer.isBlank()) return DIRECT;
    try {
      String host = URI.create(referer.strip()).getHost();
      if (host == null || host.isBlank()) return DIRECT;
      host = host.toLowerCase(Locale.ROOT);
      if (host.startsWith("www.")) host = host.substring(4);
      return host.length() > 100 ? host.substring(0, 100) : host;
    } catch (IllegalArgumentException e) {
      return DIRECT;
    }
  }

  /** Langue principale d'Accept-Language (« fr-CM,fr;q=0.9 » -> « fr »), ou null. */
  public static String primaryLanguage(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) return null;
    String first = acceptLanguage.split(",", 2)[0].split(";", 2)[0].strip();
    String lang = first.split("-", 2)[0].toLowerCase(Locale.ROOT);
    return lang.matches("[a-z]{2,3}") ? lang : null;
  }
}
