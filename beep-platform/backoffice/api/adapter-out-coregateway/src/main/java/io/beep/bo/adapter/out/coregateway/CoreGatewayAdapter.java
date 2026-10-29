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
 * Client de l'API admin de beep-core (contracts/beep-admin.openapi.yaml, v0.3). Écrit à la main : les DTO du port reprennent
 * exactement les schémas du contrat et le test de contrat vérifie le core réel. Il sera remplacé par un client généré
 * (openapi-generator) quand le contrat se stabilisera.
 * Erreurs : 4xx du core -> {@link CoreRejectedException} (code relayé) ; réseau, 5xx, réponse vide -> {@link CoreUnavailableException}.
 */
public class CoreGatewayAdapter implements CoreGatewayPort {
  @Override public Map<String,Object> ecosystem(String method,String path,Map<String,Object> body,UUID staff){return call(()->{var request=client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path).header("X-Staff-Id",staff.toString());if(!method.equals("GET"))request.contentType(MediaType.APPLICATION_JSON).body(body);return request.retrieve().body(new org.springframework.core.ParameterizedTypeReference<Map<String,Object>>(){});});}
  @Override @SuppressWarnings("unchecked") public Map<String,Object> developerMonitor(UUID staff){return call(()->client.get().uri("/developer/monitor").header("X-Staff-Id",staff.toString()).retrieve().body(Map.class));}
  @Override @SuppressWarnings("unchecked") public Map<String,Object> developerWorkspace(UUID ws,UUID staff){return call(()->client.get().uri("/developer/workspaces/{id}",ws).header("X-Staff-Id",staff.toString()).retrieve().body(Map.class));}
  @Override @SuppressWarnings("unchecked") public Map<String,Object> developerAction(UUID ws,Map<String,Object> action,UUID staff){return call(()->client.post().uri("/developer/workspaces/{id}/actions",ws).header("X-Staff-Id",staff.toString()).contentType(MediaType.APPLICATION_JSON).body(action).retrieve().body(Map.class));}
  record MetaDto(String contract) {}

  record PlanDto(String code, String name, int version, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf, @JsonProperty("public") boolean isPublic,
      Map<String, Long> entitlements) {}

  record AppsDto(List<AppView> items) {}

  record SubscriptionsDto(List<SubscriptionView> items) {}

  record InvoicesDto(List<InvoiceView> items) {}

  record CouponsDto(List<CouponView> items) {}

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
        .map(p -> new PlanView(p.code(), p.name(), p.version(), p.priceXaf(), p.priceQuarterlyXaf(), p.priceAnnualXaf(), p.isPublic(), p.entitlements())).toList();
  }

  @Override
  public List<AppView> catalog(UUID staffId) {
    return call(() -> client.get().uri("/catalog").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(AppsDto.class)).items();
  }

  @Override
  public AppView publishApp(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("name", name);
    body.put("hosts", hosts);
    body.put("iosTemplate", iosTemplate);
    body.put("androidPackage", androidPackage);
    body.put("androidTemplate", androidTemplate);
    body.put("enabled", enabled);
    return call(() -> client.put().uri("/catalog/{code}", code).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(AppView.class));
  }

  @Override
  public List<AppView> setAppEnabled(String code, boolean enabled, UUID staffId) {
    return call(() -> client.put().uri("/catalog/{code}/enabled", code).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("enabled", enabled)).retrieve().body(AppsDto.class)).items();
  }

  @Override
  public AppTestResult testApp(String url, String profile, AppView candidate, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("url", url);
    body.put("profile", profile);
    if (candidate != null) {
      Map<String, Object> c = new java.util.LinkedHashMap<>();
      c.put("code", candidate.code());
      c.put("name", candidate.name());
      c.put("hosts", candidate.hosts());
      c.put("iosTemplate", candidate.iosTemplate());
      c.put("androidPackage", candidate.androidPackage());
      c.put("androidTemplate", candidate.androidTemplate());
      body.put("candidate", c);
    }
    return call(() -> client.post().uri("/catalog/test").header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(AppTestResult.class));
  }

  @Override
  public DomainsPage domains(String query, String status, int limit, String cursor, UUID staffId) {
    return call(() -> client.get().uri(b -> page(b.path("/domains"), limit, cursor).queryParamIfPresent("q", opt(query)).queryParamIfPresent("status", opt(status)).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(DomainsPage.class));
  }

  @Override
  public DomainView domain(UUID id, UUID staffId) {
    return call(() -> client.get().uri("/domains/{id}", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(DomainView.class));
  }

  @Override
  public DomainView recheckDomain(UUID id, UUID staffId) {
    return call(() -> client.post().uri("/domains/{id}/recheck", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(DomainView.class));
  }

  @Override
  public DomainView blockDomain(UUID id, String reason, UUID staffId) {
    return call(() -> client.post().uri("/domains/{id}/block", id).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("reason", reason)).retrieve().body(DomainView.class));
  }

  @Override
  public DomainView unblockDomain(UUID id, UUID staffId) {
    return call(() -> client.post().uri("/domains/{id}/unblock", id).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(DomainView.class));
  }

  @Override
  public BillingSummary billingSummary(UUID staffId) {
    return call(() -> client.get().uri("/billing/summary").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(BillingSummary.class));
  }

  @Override
  public List<SubscriptionView> subscriptions(String status, int limit, int offset, UUID staffId) {
    return call(() -> client.get().uri(b -> b.path("/billing/subscriptions").queryParamIfPresent("status", opt(status)).queryParam("limit", limit).queryParam("offset", offset).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(SubscriptionsDto.class)).items();
  }

  @Override
  public List<SubscriptionView> upcomingRenewals(int days, int limit, UUID staffId) {
    return call(() -> client.get().uri(b -> b.path("/billing/upcoming").queryParam("days", days).queryParam("limit", limit).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(SubscriptionsDto.class)).items();
  }

  @Override
  public PaymentsPage payments(String status, UUID workspaceId, int limit, String cursor, UUID staffId) {
    return call(() -> client.get().uri(b -> page(b.path("/billing/payments"), limit, cursor).queryParamIfPresent("status", opt(status))
            .queryParamIfPresent("workspaceId", java.util.Optional.ofNullable(workspaceId)).build())
        .header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(PaymentsPage.class));
  }

  @Override
  public List<InvoiceView> workspaceInvoices(UUID workspaceId, UUID staffId) {
    return call(() -> client.get().uri("/workspaces/{id}/invoices", workspaceId).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(InvoicesDto.class)).items();
  }

  @Override
  public byte[] invoicePdf(String number, UUID staffId) {
    return call(() -> client.get().uri("/billing/invoices/{number}/pdf", number).header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(byte[].class));
  }

  @Override
  public ManualPaymentResult recordManualPayment(UUID workspaceId, String plan, String cycle, long amountXaf, String reference, String country, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("plan", plan);
    body.put("cycle", cycle);
    body.put("amountXaf", amountXaf);
    body.put("reference", reference);
    if (country != null && !country.isBlank()) body.put("country", country);
    return call(() -> client.post().uri("/workspaces/{id}/payments", workspaceId).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(body).retrieve().body(ManualPaymentResult.class));
  }

  @Override
  public SubscriptionView changePlan(UUID workspaceId, String plan, java.time.Instant until, String reason, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("plan", plan);
    body.put("until", until == null ? null : until.toString());
    body.put("reason", reason);
    return call(() -> client.put().uri("/workspaces/{id}/plan", workspaceId).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(body).retrieve().body(SubscriptionView.class));
  }

  @Override
  public List<CouponView> coupons(UUID staffId) {
    return call(() -> client.get().uri("/coupons").header("X-Staff-Id", String.valueOf(staffId)).retrieve().body(CouponsDto.class)).items();
  }

  @Override
  public CouponView createCoupon(String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, java.time.Instant validUntil, List<String> plans, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("code", code);
    if (percentOff != null) body.put("percentOff", percentOff);
    if (amountOffXaf != null) body.put("amountOffXaf", amountOffXaf);
    body.put("maxRedemptions", maxRedemptions);
    body.put("validUntil", validUntil == null ? null : validUntil.toString());
    body.put("plans", plans);
    return call(() -> client.post().uri("/coupons").header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
        .body(CouponView.class));
  }

  @Override
  public CouponView setCouponActive(String code, boolean active, UUID staffId) {
    return call(() -> client.put().uri("/coupons/{code}/active", code).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("active", active)).retrieve().body(CouponView.class));
  }

  @Override
  public PlanView publishPlan(String code, String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, boolean isPublic, Map<String, Long> entitlements, UUID staffId) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("name", name);
    body.put("priceXaf", priceXaf);
    body.put("priceQuarterlyXaf", priceQuarterlyXaf);
    body.put("priceAnnualXaf", priceAnnualXaf);
    body.put("public", isPublic);
    body.put("entitlements", entitlements);
    PlanDto p = call(() -> client.put().uri("/plans/{code}", code).header("X-Staff-Id", String.valueOf(staffId)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
        .body(PlanDto.class));
    return new PlanView(p.code(), p.name(), p.version(), p.priceXaf(), p.priceQuarterlyXaf(), p.priceAnnualXaf(), p.isPublic(), p.entitlements());
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

  @Override public Reports reports(String status,int limit,int offset,UUID staff) {
    return call(() -> client.get().uri(b -> b.path("/moderation/reports").queryParamIfPresent("status",java.util.Optional.ofNullable(status)).queryParam("limit",limit).queryParam("offset",offset).build()).header("X-Staff-Id",staff.toString()).retrieve().body(Reports.class));
  }
  @Override public AbuseReport resolveReport(UUID id,String decision,String reason,UUID staff) {
    return call(() -> client.post().uri("/moderation/reports/{id}/resolve",id).header("X-Staff-Id",staff.toString()).contentType(MediaType.APPLICATION_JSON).body(Map.of("decision",decision,"reason",reason)).retrieve().body(AbuseReport.class));
  }
  @Override public BlockedHosts blockedHosts(UUID staff) {return call(() -> client.get().uri("/moderation/blocklist").header("X-Staff-Id",staff.toString()).retrieve().body(BlockedHosts.class));}
  @Override public BlockedHosts blockHost(String host,boolean blocked,String reason,UUID staff) {
    return call(() -> client.put().uri("/moderation/blocklist/{host}",host).header("X-Staff-Id",staff.toString()).contentType(MediaType.APPLICATION_JSON).body(Map.of("blocked",blocked,"reason",reason)).retrieve().body(BlockedHosts.class));
  }
  @Override public FeatureFlags featureFlags(UUID staff) {return call(() -> client.get().uri("/feature-flags").header("X-Staff-Id",staff.toString()).retrieve().body(FeatureFlags.class));}
  @Override public FeatureFlag publishFlag(String key,boolean enabled,int rollout,int version,String reason,UUID staff) {
    return call(() -> client.put().uri("/feature-flags/{key}",key).header("X-Staff-Id",staff.toString()).contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled",enabled,"rolloutPercent",rollout,"version",version,"reason",reason)).retrieve().body(FeatureFlag.class));
  }
  @Override public Map<String,Object> exportUser(UUID id,UUID staff) {
    return call(() -> client.post().uri("/users/{id}/export",id).header("X-Staff-Id",staff.toString()).retrieve().body(new org.springframework.core.ParameterizedTypeReference<Map<String,Object>>() {}));
  }
  @Override public void eraseUser(UUID id,String reason,UUID staff) {
    call(() -> client.post().uri("/users/{id}/erase",id).header("X-Staff-Id",staff.toString()).contentType(MediaType.APPLICATION_JSON).body(Map.of("reason",reason)).retrieve().body(Map.class));
  }

}
