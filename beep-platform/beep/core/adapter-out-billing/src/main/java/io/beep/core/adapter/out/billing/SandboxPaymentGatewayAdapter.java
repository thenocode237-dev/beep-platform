package io.beep.core.adapter.out.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.core.app.port.PaymentGatewayPort;
import java.io.IOException;
import java.util.UUID;

/**
 * Agrégateur factice pour le développement et les tests de bout en bout : aucun appel réseau, aucun argent.
 * Sans état : l'issue est codée dans la référence. Un numéro Mobile Money se terminant par 0000 est refusé, un numéro se terminant
 * par 1111 reste en attente ; tout autre paiement réussit dès la première vérification.
 * Interdit en production (le démarrage échoue si PAYMENT_PROVIDER=sandbox avec le profil prod).
 */
public class SandboxPaymentGatewayAdapter implements PaymentGatewayPort {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final String webhookSecret;

  public SandboxPaymentGatewayAdapter(String webhookSecret) {
    this.webhookSecret = webhookSecret;
  }

  @Override
  public String name() {
    return "sandbox";
  }

  @Override
  public Charge initiate(ChargeRequest r) {
    String outcome = r.phone() != null && r.phone().endsWith("0000") ? "fail" : r.phone() != null && r.phone().endsWith("1111") ? "wait" : "ok";
    return new Charge("sbx_" + outcome + "_" + UUID.randomUUID(), r.method().mobileMoney() ? null : "/sandbox/checkout");
  }

  @Override
  public Verification verify(String providerRef) {
    if (providerRef.startsWith("sbx_ok_")) return new Verification(State.SUCCEEDED, null);
    if (providerRef.startsWith("sbx_fail_")) return new Verification(State.FAILED, "declined");
    return new Verification(State.PENDING, null);
  }

  @Override
  public boolean validSignature(String rawBody, String signature) {
    return Hmac.valid(webhookSecret, rawBody, signature);
  }

  @Override
  public String referenceOf(String rawBody) {
    try {
      JsonNode node = JSON.readTree(rawBody).path("reference");
      return node.isTextual() ? node.asText() : null;
    } catch (IOException e) {
      return null;
    }
  }
}
