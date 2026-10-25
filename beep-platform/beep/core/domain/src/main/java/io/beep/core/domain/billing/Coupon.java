package io.beep.core.domain.billing;

import io.beep.core.domain.DomainException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Code promo : remise en pourcentage OU en montant fixe, plafonnée en utilisations et en durée, limitée à certains plans. */
public record Coupon(String code, int percentOff, long amountOffXaf, Integer maxRedemptions, int redeemed, Instant validUntil, Set<String> planCodes,
    boolean active, Instant createdAt) {
  private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,31}");

  public Coupon {
    planCodes = Set.copyOf(planCodes);
  }

  public static String normalize(String raw) {
    String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!CODE.matcher(code).matches()) throw new DomainException("invalid_coupon");
    return code;
  }

  public static Coupon create(String code, int percentOff, long amountOffXaf, Integer maxRedemptions, Instant validUntil, Set<String> planCodes, Instant now) {
    boolean percent = percentOff > 0;
    boolean amount = amountOffXaf > 0;
    if (percent == amount || percentOff < 0 || percentOff > 100 || amountOffXaf < 0 || (maxRedemptions != null && maxRedemptions < 1)) throw new DomainException("invalid_coupon");
    return new Coupon(normalize(code), percentOff, amountOffXaf, maxRedemptions, 0, validUntil, planCodes, true, now);
  }

  /** Remise sur un prix de liste ; lève une erreur métier si le code n'est pas utilisable. */
  public long discount(long listPrice, String planCode, Instant now) {
    if (!active) throw new DomainException("coupon_inactive");
    if (validUntil != null && !now.isBefore(validUntil)) throw new DomainException("coupon_expired");
    if (maxRedemptions != null && redeemed >= maxRedemptions) throw new DomainException("coupon_exhausted");
    if (!planCodes.isEmpty() && !planCodes.contains(planCode)) throw new DomainException("coupon_not_applicable");
    long off = percentOff > 0 ? listPrice * percentOff / 100 : amountOffXaf;
    return Math.min(listPrice, off);
  }
}
