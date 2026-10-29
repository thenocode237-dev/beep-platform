package io.beep.core.domain.link;

import io.beep.core.domain.DomainException;
import java.util.regex.Pattern;

/**
 * Chemin public `beepclick.app/{slug}`, partagé par les liens courts et les pages bio.
 * Sensible à la casse (base62) ; les slugs réservés sont comparés sans la casse.
 */
public record Slug(String value) {
  public static final int MAX_LENGTH = 64;
  public static final int MIN_CUSTOM_LENGTH = 3;
  private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0," + (MAX_LENGTH - 1) + "}$");

  public Slug {
    if (value == null || !SHAPE.matcher(value).matches()) throw new DomainException("invalid_slug");
  }

  /** Slug saisi par l'utilisateur : 3 caractères minimum, jamais un slug réservé. */
  public static Slug custom(String raw) {
    if (raw == null) throw new DomainException("invalid_slug");
    String v = raw.trim();
    if (v.length() < MIN_CUSTOM_LENGTH) throw new DomainException("invalid_slug");
    Slug slug = new Slug(v);
    if (ReservedSlugs.isReserved(v)) throw new DomainException("reserved_slug");
    return slug;
  }

  /** Forme valide d'un chemin entrant (sans contrôle de réservation) : évite une requête en base pour un chemin impossible. */
  public static boolean looksValid(String raw) {
    return raw != null && SHAPE.matcher(raw).matches();
  }

  @Override
  public String toString() {
    return value;
  }
}
