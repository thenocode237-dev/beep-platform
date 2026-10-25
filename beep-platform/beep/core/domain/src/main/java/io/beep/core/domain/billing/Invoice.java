package io.beep.core.domain.billing;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Facture d'un paiement confirmé. Numérotation séquentielle sans trou, par année civile (BEEP-2026-000001), attribuée par la base
 * dans la transaction du paiement. Montants en FCFA : `grossXaf` TTC = `netXaf` HT + `vatXaf`.
 */
public record Invoice(String number, UUID paymentId, UUID workspaceId, Instant issuedAt, String customerName, String customerEmail, String country,
    String planName, BillingCycle cycle, Instant periodStart, Instant periodEnd, long listPriceXaf, long discountXaf, long creditXaf, long grossXaf,
    long netXaf, long vatXaf, int vatRateBp, PaymentMethod method) {

  public static String format(int year, long sequence) {
    return String.format(Locale.ROOT, "BEEP-%d-%06d", year, sequence);
  }
}
