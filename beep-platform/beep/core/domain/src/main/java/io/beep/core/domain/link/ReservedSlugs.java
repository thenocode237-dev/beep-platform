package io.beep.core.domain.link;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Slugs réservés (specs §8.4). Doit couvrir TOUS les chemins routés vers beep-web dans les Caddyfile :
 * `scripts/reserved-slugs-check.mjs` (CI, `make lint`) compare cette liste aux Caddyfile.
 * Comparaison insensible à la casse.
 */
public final class ReservedSlugs {
  private ReservedSlugs() {}

  /** Chemins du site, routes techniques et préfixes de langue. */
  static final Set<String> SYSTEM = Set.of(
      "app", "login", "signup", "pricing", "blog", "features", "docs", "roadmap", "changelog", "affiliates", "report", "legal",
      "api", "v1", "internal", "actuator", "admin", "dashboard", "static", "assets", "healthz", "beep.js", "_b", "_next",
      ".well-known", "favicon.ico", "robots.txt", "sitemap.xml", "icon.svg",
      "en", "fr", "q", "forgot-password", "reset-password", "verify-email");

  /** Noms protégés (marque, usurpation de services). */
  static final Set<String> PROTECTED = Set.of(
      "beep", "www", "mail", "support", "help", "status", "security", "abuse", "billing", "account", "settings", "privacy", "terms");

  public static boolean isReserved(String slug) {
    String s = slug.toLowerCase(Locale.ROOT);
    return SYSTEM.contains(s) || PROTECTED.contains(s);
  }

  public static Set<String> all() {
    TreeSet<String> all = new TreeSet<>(SYSTEM);
    all.addAll(PROTECTED);
    return all;
  }
}
