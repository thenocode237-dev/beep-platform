package io.beep.core.adapter.out.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.core.app.port.PaymentGatewayPort;
import io.beep.core.domain.billing.PaymentMethod;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Notch Pay (https://developer.notchpay.co) : MTN MoMo et Orange Money en XAF, cartes via la page de paiement hébergée.
 *
 * <ul>
 *   <li>`POST /payments` crée la transaction ; Mobile Money : `POST /payments/{référence}` envoie la demande de confirmation au téléphone ;
 *   <li>`GET /payments/{référence}` donne l'état réel (`complete`, `failed`, `canceled`, `expired`, sinon en attente) — seule source crue ;
 *   <li>les notifications portent la signature HMAC-SHA256 du corps brut (en-tête `x-notch-signature`, clé de hachage du tableau de bord).
 * </ul>
 *
 * Intégration écrite d'après la documentation publique : à valider contre le bac à sable Notch Pay avant la production
 * (critère de sortie « test de bout en bout MTN MoMo et Orange Money »).
 */
public class NotchPayGatewayAdapter implements PaymentGatewayPort {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
  private final String baseUrl;
  private final String publicKey;
  private final String hashKey;

  public NotchPayGatewayAdapter(String baseUrl, String publicKey, String hashKey) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.publicKey = publicKey;
    this.hashKey = hashKey;
  }

  @Override
  public String name() {
    return "notchpay";
  }

  @Override
  public Charge initiate(ChargeRequest r) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", r.amountXaf());
    body.put("currency", "XAF");
    body.put("email", r.customerEmail());
    body.put("reference", r.reference());
    body.put("description", r.description());
    if (r.callbackUrl() != null && !r.callbackUrl().isBlank()) body.put("callback", r.callbackUrl());
    if (r.phone() != null) body.put("phone", "+" + r.phone());
    JsonNode created = call("POST", "/payments", body);
    String ref = created.path("transaction").path("reference").asText(null);
    if (ref == null) throw new GatewayException("réponse Notch Pay sans référence", null);
    if (r.method().mobileMoney()) {
      // Demande poussée sur le téléphone (USSD / confirmation Mobile Money).
      Map<String, Object> channel = new LinkedHashMap<>();
      channel.put("channel", channel(r.method(), r.phone()));
      channel.put("data", Map.of("phone", "+" + r.phone()));
      call("POST", "/payments/" + ref, channel);
      return new Charge(ref, null);
    }
    String url = created.path("authorization_url").asText(null);
    if (url == null) throw new GatewayException("réponse Notch Pay sans page de paiement", null);
    return new Charge(ref, url);
  }

  @Override
  public Verification verify(String providerRef) {
    JsonNode tx = call("GET", "/payments/" + providerRef, null).path("transaction");
    return switch (tx.path("status").asText("").toLowerCase(Locale.ROOT)) {
      case "complete", "completed", "success", "succeeded" -> new Verification(State.SUCCEEDED, null);
      case "failed", "canceled", "cancelled", "expired", "rejected" -> new Verification(State.FAILED, tx.path("status").asText());
      default -> new Verification(State.PENDING, null);
    };
  }

  @Override
  public boolean validSignature(String rawBody, String signature) {
    return Hmac.valid(hashKey, rawBody, signature);
  }

  @Override
  public String referenceOf(String rawBody) {
    try {
      JsonNode root = JSON.readTree(rawBody);
      JsonNode data = root.path("data");
      String ref = data.path("reference").asText(null);
      return ref != null ? ref : root.path("transaction").path("reference").asText(null);
    } catch (IOException e) {
      return null;
    }
  }

  /** Canal Mobile Money : préfixe pays de l'indicatif du numéro (237 Cameroun → `cm`), opérateur MTN ou Orange. */
  static String channel(PaymentMethod method, String phone) {
    String country = phone.startsWith("241") ? "ga" : "cm";
    return country + (method == PaymentMethod.ORANGE_MONEY ? ".orange" : ".mtn");
  }

  private JsonNode call(String method, String path, Object body) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15)).header("Authorization", publicKey)
          .header("Accept", "application/json");
      if (body == null) {
        b.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      }
      HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) throw new GatewayException("Notch Pay " + method + " " + path + " : HTTP " + response.statusCode(), null);
      return JSON.readTree(response.body());
    } catch (IOException e) {
      throw new GatewayException("Notch Pay injoignable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GatewayException("appel Notch Pay interrompu", e);
    }
  }
}
