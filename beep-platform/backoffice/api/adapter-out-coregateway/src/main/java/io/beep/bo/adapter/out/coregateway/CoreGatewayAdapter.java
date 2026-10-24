package io.beep.bo.adapter.out.coregateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.bo.app.port.CoreGatewayPort;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Client de l'API admin de beep-core (contracts/beep-admin.openapi.yaml, v0.2). Écrit à la main : les DTO du port reprennent
 * exactement les schémas du contrat et le test de contrat vérifie le core réel. Il sera remplacé par un client généré
 * (openapi-generator) quand le contrat se stabilisera.
 * Erreurs : 4xx du core -> {@link CoreRejectedException} (code relayé) ; réseau, 5xx, réponse vide -> {@link CoreUnavailableException}.
 */
public class CoreGatewayAdapter implements CoreGatewayPort {
  record MetaDto(String contract) {}

  record PlanDto(String code, String name, int version, long priceXaf, @JsonProperty("public") boolean isPublic, Map<String, Long> entitlements) {}

  record PlansDto(List<PlanDto> items) {}

  record ReservedDto(List<String> items) {}

  record JobsDto(List<JobView> items) {}

  private static final ObjectMapper JSON = new ObjectMapper();

  private final RestClient client;

  public CoreGatewayAdapter(String baseUrl, String token) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
  }

  @Override
  public UsersPage listUsers(int limit, String cursor, String query, UUID staffId) {
    return call(() -> client.get().uri(b -> page(b.path("/users"), limit, cursor).queryParamIfPresent("q", opt(query)).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(UsersPage.class));
  }

  @Override
  public UserDetail getUser(UUID id, UUID staffId) {
    return call(() -> client.get().uri("/users/{id}", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(UserDetail.class));
  }

  @Override
  public UserView setUserStatus(UUID id, String status, String reason, UUID staffId) {
    return call(() -> client.patch().uri("/users/{id}/status", id).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("status", status, "reason", reason)).retrieve().body(UserView.class));
  }

  @Override
  public LinksPage listLinks(String query, UUID workspaceId, int limit, String cursor, UUID staffId) {
    return call(() -> client.get().uri(b -> page(b.path("/links"), limit, cursor).queryParamIfPresent("q", opt(query))
            .queryParamIfPresent("workspaceId", java.util.Optional.ofNullable(workspaceId)).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(LinksPage.class));
  }

  @Override
  public LinkView getLink(UUID id, UUID staffId) {
    return call(() -> client.get().uri("/links/{id}", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(LinkView.class));
  }

  @Override
  public LinkView disableLink(UUID id, String reason, UUID staffId) {
    return call(() -> client.post().uri("/links/{id}/disable", id).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("reason", reason)).retrieve().body(LinkView.class));
  }

  @Override
  public LinkView enableLink(UUID id, UUID staffId) {
    return call(() -> client.post().uri("/links/{id}/enable", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(LinkView.class));
  }

  @Override
  public List<String> reservedSlugs(UUID staffId) {
    return call(() -> client.get().uri("/reserved-slugs").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(ReservedDto.class)).items();
  }

  @Override
  public List<PlanView> plans(UUID staffId) {
    return call(() -> client.get().uri("/plans").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(PlansDto.class)).items().stream()
        .map(p -> new PlanView(p.code(), p.name(), p.version(), p.priceXaf(), p.isPublic(), p.entitlements())).toList();
  }

  @Override
  public WorkspaceEntitlements setEntitlements(UUID workspaceId, Map<String, Long> overrides, String reason, UUID staffId) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("overrides", overrides);
    body.put("reason", reason);
    return call(() -> client.put().uri("/workspaces/{id}/entitlements", workspaceId).header("X-Staff-Id", String.valueOf(staffId))
        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(WorkspaceEntitlements.class));
  }

  @Override
  public PlatformMetrics metrics(int days, UUID staffId) {
    return call(() -> client.get().uri(b -> b.path("/metrics").queryParam("days", days).build()).header("X-Staff-Id", String.valueOf(staffId))
        .retrieve().body(PlatformMetrics.class));
  }

  @Override
  public List<JobView> jobs(UUID staffId) {
    return call(() -> client.get().uri("/jobs").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(JobsDto.class)).items();
  }

  @Override
  public InvitationsPage invitations(int limit, String cursor, UUID staffId) {
    return call(() -> client.get().uri(b -> page(b.path("/invitations"), limit, cursor).build()).header("X-Staff-Id", String.valueOf(staffId))
        .retrieve().body(InvitationsPage.class));
  }

  @Override
  public InvitationView invite(String email, String locale, UUID staffId) {
    return call(() -> client.post().uri("/invitations").header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "locale", locale)).retrieve().body(InvitationView.class));
  }

  @Override
  public String contractVersion() {
    return call(() -> client.get().uri("/meta").retrieve().body(MetaDto.class)).contract();
  }

  @Override
  public boolean isReachable() {
    try {
      contractVersion();
      return true;
    } catch (CoreUnavailableException | CoreRejectedException e) {
      return false;
    }
  }

  private static UriBuilder page(UriBuilder b, int limit, String cursor) {
    UriBuilder u = b.queryParam("limit", limit);
    return cursor == null || cursor.isBlank() ? u : u.queryParam("cursor", cursor);
  }

  private static java.util.Optional<String> opt(String s) {
    return s == null || s.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(s.strip());
  }

  private static <T> T call(Supplier<T> request) {
    T body;
    try {
      body = request.get();
    } catch (HttpClientErrorException e) {
      throw new CoreRejectedException(e.getStatusCode().value(), code(e.getResponseBodyAsString()));
    } catch (RestClientException e) {
      throw new CoreUnavailableException("beep-core injoignable", e);
    }
    if (body == null) throw new CoreUnavailableException("réponse vide du core", null);
    return body;
  }

  private static String code(String problem) {
    try {
      JsonNode n = JSON.readTree(problem);
      return n.path("code").asText("invalid_request");
    } catch (Exception e) {
      return "invalid_request";
    }
  }
}
