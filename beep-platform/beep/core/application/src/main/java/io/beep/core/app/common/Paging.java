package io.beep.core.app.common;

/** Bornes communes des listes paginées. */
public final class Paging {
  public static final int MAX_LIMIT = 100;

  private Paging() {}

  public static int limit(int requested) {
    return Math.max(1, Math.min(requested, MAX_LIMIT));
  }

  /** Recherche saisie : espaces retirés, vide = aucune, bornée. */
  public static String query(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String q = raw.strip();
    return q.length() > 254 ? q.substring(0, 254) : q;
  }
}
