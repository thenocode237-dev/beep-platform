package io.beep.core.domain.link;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Slugs générés : base62, 7 caractères (62^7 ≈ 3,5·10^12 combinaisons), imprévisibles. */
public final class SlugGenerator {
  public static final int LENGTH = 7;
  private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private SlugGenerator() {}

  public static Slug next() {
    return next(RANDOM, LENGTH);
  }

  public static Slug next(RandomGenerator random, int length) {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    String v = new String(out);
    // Collision avec un mot réservé quasi impossible à 7 caractères, mais on ne la laisse jamais passer.
    return ReservedSlugs.isReserved(v) ? next(random, length) : new Slug(v);
  }

  /** Code court opaque (QR dynamiques, identifiants de blocs). */
  public static String code(int length) {
    return next(RANDOM, length).value();
  }
}
