package io.beep.core.domain.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Tentative de paiement d'une période. `amountXaf` est le montant TTC réellement dû (remise et crédit de prorata déduits) ;
 * `listPriceXaf` le prix de liste, conservé pour la facture. Le paiement n'a d'effet qu'une fois confirmé par l'agrégateur.
 *
 * @param providerRef référence chez l'agrégateur (ou `manual-…` pour un paiement enregistré par le staff)
 * @param phoneHint   quatre derniers chiffres du numéro Mobile Money : le numéro complet n'est jamais conservé
 */
public record Payment(UUID id, UUID workspaceId, String planCode, BillingCycle cycle, PaymentMethod method, String provider, String providerRef,
    PaymentStatus status, long listPriceXaf, long discountXaf, long creditXaf, long amountXaf, String couponCode, String country, String phoneHint,
    String note, String recordedBy, String failureReason, Instant createdAt, Instant paidAt) {

  public Payment withProviderRef(String ref) {
    return new Payment(id, workspaceId, planCode, cycle, method, provider, ref, status, listPriceXaf, discountXaf, creditXaf, amountXaf,
        couponCode, country, phoneHint, note, recordedBy, failureReason, createdAt, paidAt);
  }

  public Payment succeeded(Instant at) {
    return new Payment(id, workspaceId, planCode, cycle, method, provider, providerRef, PaymentStatus.SUCCEEDED, listPriceXaf, discountXaf, creditXaf, amountXaf,
        couponCode, country, phoneHint, note, recordedBy, null, createdAt, at);
  }

  public Payment failed(String reason) {
    return new Payment(id, workspaceId, planCode, cycle, method, provider, providerRef, PaymentStatus.FAILED, listPriceXaf, discountXaf, creditXaf, amountXaf,
        couponCode, country, phoneHint, note, recordedBy, reason, createdAt, null);
  }

  public Payment refunded() {
    return new Payment(id, workspaceId, planCode, cycle, method, provider, providerRef, PaymentStatus.REFUNDED, listPriceXaf, discountXaf, creditXaf, amountXaf,
        couponCode, country, phoneHint, note, recordedBy, failureReason, createdAt, paidAt);
  }
}
