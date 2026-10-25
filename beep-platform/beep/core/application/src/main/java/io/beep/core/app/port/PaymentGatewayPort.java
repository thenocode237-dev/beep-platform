package io.beep.core.app.port;

import io.beep.core.domain.billing.PaymentMethod;

/**
 * Agrégateur de paiement régional (Mobile Money MTN / Orange, cartes). Le core ne fait jamais confiance au contenu d'un webhook :
 * il interroge `verify` auprès du fournisseur avant de créditer un abonnement.
 */
public interface PaymentGatewayPort {
  /** Nom stocké avec chaque paiement (`notchpay`, `sandbox`). */
  String name();

  Charge initiate(ChargeRequest request);

  /** État réel du paiement chez le fournisseur. */
  Verification verify(String providerRef);

  /** Vérifie l'authenticité d'une notification (signature HMAC du corps brut). */
  boolean validSignature(String rawBody, String signature);

  /** Extrait la référence fournisseur d'une notification dont la signature a été validée. */
  String referenceOf(String rawBody);

  /** Fournisseur injoignable ou réponse inattendue : le paiement reste en l'état, il sera rapproché plus tard. */
  class GatewayException extends RuntimeException {
    public GatewayException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  record ChargeRequest(String reference, long amountXaf, PaymentMethod method, String phone, String customerEmail, String description, String callbackUrl) {}

  /** `redirectUrl` : page de paiement hébergée (carte) ; absente pour Mobile Money, confirmé sur le téléphone. */
  record Charge(String providerRef, String redirectUrl) {}

  enum State { PENDING, SUCCEEDED, FAILED }

  record Verification(State state, String failureReason) {}
}
