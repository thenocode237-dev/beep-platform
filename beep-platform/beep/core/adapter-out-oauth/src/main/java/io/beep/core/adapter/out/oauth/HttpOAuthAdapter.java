package io.beep.core.adapter.out.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.core.app.port.OAuthPort;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google (OpenID Connect, userinfo) et GitHub (adresse principale vérifiée via /user/emails).
 * Un fournisseur n'est actif que si son identifiant et son secret client sont configurés.
 */
public class HttpOAuthAdapter implements OAuthPort {
  public record Client(String id, String secret) {
    boolean configured() {
      return id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Map<Provider, Client> clients = new EnumMap<>(Provider.class);
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

  public HttpOAuthAdapter(Client google, Client github) {
    clients.put(Provider.GOOGLE, google);
    clients.put(Provider.GITHUB, github);
  }

  @Override
  public boolean enabled(Provider provider) {
    Client c = clients.get(provider);
    return c != null && c.configured();
  }

  @Override
  public String authorizationUrl(Provider provider, String state, String codeChallenge, String redirectUri) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("client_id", clients.get(provider).id());
    q.put("redirect_uri", redirectUri);
    q.put("response_type", "code");
    q.put("scope", provider == Provider.GOOGLE ? "openid email" : "user:email");
    q.put("state", state);
    q.put("code_challenge", codeChallenge);
    q.put("code_challenge_method", "S256");
    if (provider == Provider.GOOGLE) q.put("prompt", "select_account");
    String base = provider == Provider.GOOGLE ? "https://accounts.google.com/o/oauth2/v2/auth" : "https://github.com/login/oauth/authorize";
    return base + "?" + form(q);
  }

  @Override
  public Identity exchange(Provider provider, String code, String codeVerifier, String redirectUri) {
    Client c = clients.get(provider);
    Map<String, String> body = new LinkedHashMap<>();
    body.put("grant_type", "authorization_code");
    body.put("code", code);
    body.put("redirect_uri", redirectUri);
    body.put("client_id", c.id());
    body.put("client_secret", c.secret());
    body.put("code_verifier", codeVerifier);
    String tokenUrl = provider == Provider.GOOGLE ? "https://oauth2.googleapis.com/token" : "https://github.com/login/oauth/access_token";
    JsonNode token = send(HttpRequest.newBuilder(URI.create(tokenUrl)).header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(body))));
    String access = token.path("access_token").asText(null);
    if (access == null) throw new OAuthFailedException("pas de jeton d'accès (" + token.path("error").asText("?") + ")", null);

    if (provider == Provider.GOOGLE) {
      JsonNode info = send(get("https://openidconnect.googleapis.com/v1/userinfo", access));
      return new Identity(info.path("email").asText(null), info.path("email_verified").asBoolean(false), info.path("sub").asText(null));
    }
    JsonNode user = send(get("https://api.github.com/user", access));
    JsonNode emails = send(get("https://api.github.com/user/emails", access));
    for (JsonNode e : emails) {
      if (e.path("primary").asBoolean(false)) return new Identity(e.path("email").asText(null), e.path("verified").asBoolean(false), user.path("id").asText(null));
    }
    return new Identity(null, false, user.path("id").asText(null));
  }

  private static HttpRequest.Builder get(String url, String accessToken) {
    return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + accessToken).header("Accept", "application/json")
        .header("User-Agent", "Beep").GET();
  }

  private JsonNode send(HttpRequest.Builder request) {
    try {
      HttpResponse<String> res = http.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() / 100 != 2) throw new OAuthFailedException("réponse " + res.statusCode() + " du fournisseur", null);
      return JSON.readTree(res.body());
    } catch (IOException e) {
      throw new OAuthFailedException("fournisseur injoignable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OAuthFailedException("interrompu", e);
    }
  }

  private static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
