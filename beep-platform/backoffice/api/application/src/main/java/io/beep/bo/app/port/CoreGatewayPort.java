package io.beep.bo.app.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seul accès aux données métier de beep-core : l'API admin interne (contrat OpenAPI 0.2). Jamais la base de BeepClick.
 * Les DTO reprennent exactement les schémas du contrat.
 */
public interface CoreGatewayPort {
  default Map<String,Object> ecosystem(String method,String path,Map<String,Object> body,UUID staff){throw new UnsupportedOperationException();}
  default Map<String,Object> developerMonitor(UUID staff){throw new UnsupportedOperationException();}
  default Map<String,Object> developerWorkspace(UUID ws,UUID staff){throw new UnsupportedOperationException();}
  default Map<String,Object> developerAction(UUID ws,Map<String,Object> action,UUID staff){throw new UnsupportedOperationException();}
  UsersPage listUsers(int limit, String cursor, String query, UUID staffId);

  UserDetail getUser(UUID id, UUID staffId);

  UserView setUserStatus(UUID id, String status, String reason, UUID staffId);

  LinksPage listLinks(String query, UUID workspaceId, int limit, String cursor, UUID staffId);

  LinkView getLink(UUID id, UUID staffId);

  LinkView disableLink(UUID id, String reason, UUID staffId);

  LinkView enableLink(UUID id, UUID staffId);

  List<String> reservedSlugs(UUID staffId);

  List<PlanView> plans(UUID staffId);

  WorkspaceEntitlements setEntitlements(UUID workspaceId, Map<String, Long> overrides, String reason, UUID staffId);

  PlatformMetrics metrics(int days, UUID staffId);

  List<JobView> jobs(UUID staffId);

  InvitationsPage invitations(int limit, String cursor, UUID staffId);

  InvitationView invite(String email, String locale, UUID staffId);

  // ---- Catalogue d'applications (contrat 0.5) ----
  List<AppView> catalog(UUID staffId);

  AppView publishApp(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, UUID staffId);

  List<AppView> setAppEnabled(String code, boolean enabled, UUID staffId);

  AppTestResult testApp(String url, String profile, AppView candidate, UUID staffId);

  // ---- Domaines personnalisés (contrat 0.4) ----
  DomainsPage domains(String query, String status, int limit, String cursor, UUID staffId);

  DomainView domain(UUID id, UUID staffId);

  DomainView recheckDomain(UUID id, UUID staffId);

  DomainView blockDomain(UUID id, String reason, UUID staffId);

  DomainView unblockDomain(UUID id, UUID staffId);

  // ---- Facturation (contrat 0.3) ----
  BillingSummary billingSummary(UUID staffId);

  List<SubscriptionView> subscriptions(String status, int limit, int offset, UUID staffId);

  List<SubscriptionView> upcomingRenewals(int days, int limit, UUID staffId);

  PaymentsPage payments(String status, UUID workspaceId, int limit, String cursor, UUID staffId);

  List<InvoiceView> workspaceInvoices(UUID workspaceId, UUID staffId);

  byte[] invoicePdf(String number, UUID staffId);

  ManualPaymentResult recordManualPayment(UUID workspaceId, String plan, String cycle, long amountXaf, String reference, String country, UUID staffId);

  SubscriptionView changePlan(UUID workspaceId, String plan, Instant until, String reason, UUID staffId);

  List<CouponView> coupons(UUID staffId);

  CouponView createCoupon(String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, Instant validUntil, List<String> plans, UUID staffId);

  CouponView setCouponActive(String code, boolean active, UUID staffId);

  PlanView publishPlan(String code, String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, boolean isPublic, Map<String, Long> entitlements, UUID staffId);

  /** Version du contrat admin implémentée par le core (ex. « 0.2.0 »). */
  String contractVersion();

  boolean isReachable();

  record UserView(UUID id, String email, String status, boolean emailVerified, boolean totpEnabled, Instant createdAt) {}

  record UsersPage(List<UserView> items, String nextCursor) {}

  record WorkspaceView(UUID id, String name, String plan, String status, Instant createdAt, Instant firstLinkAt) {}

  record UsageView(String resource, long used, long limit, String period, String level) {}

  record OverrideView(String key, long value, String reason, Instant updatedAt, String updatedBy) {}

  record UserDetail(UserView user, WorkspaceView workspace, List<UsageView> usage, Map<String, Long> entitlements, List<OverrideView> overrides) {}

  record LinkView(UUID id, String slug, String destination, String title, String status, String disabledReason, UUID workspaceId, String ownerEmail,
      Instant createdAt, Instant updatedAt) {}

  record LinksPage(List<LinkView> items, String nextCursor) {}

  record PlanView(String code, String name, int version, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf, boolean isPublic, Map<String, Long> entitlements) {}

  record AppView(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, int version) {}

  record AppTestResult(String profile, boolean appFound, String appName, String mode, String webUrl, String iosUrl, String androidUrl, String os, boolean inApp, String note) {}

  record DomainView(UUID id, UUID workspaceId, String ownerEmail, String hostname, String dnsStatus, String tlsStatus, boolean isDefault, boolean blocked, String blockedReason,
      String lastError, long links, Instant verifiedAt, Instant lastCheckAt, Instant createdAt) {}

  record DomainsPage(List<DomainView> items, String nextCursor) {}

  record BillingSummary(long trialing, long active, long grace, long expired, long mrrXaf, long arrXaf, long revenue30dXaf, long pendingPayments, long failedPayments) {}

  record SubscriptionView(UUID workspaceId, String workspaceName, String ownerEmail, String plan, String status, String cycle, Instant periodStart, Instant periodEnd,
      Instant graceEnd, long monthlyRateXaf) {}

  record PaymentView(UUID id, UUID workspaceId, String workspaceName, String ownerEmail, String plan, String cycle, String method, String provider, String status,
      long amountXaf, long listPriceXaf, long discountXaf, long creditXaf, String coupon, String country, String note, String recordedBy, String failureReason,
      Instant createdAt, Instant paidAt) {}

  record PaymentsPage(List<PaymentView> items, String nextCursor) {}

  record InvoiceView(String number, UUID paymentId, String plan, String cycle, long grossXaf, long netXaf, long vatXaf, int vatRateBp, String country, Instant issuedAt) {}

  record ManualPaymentResult(PaymentView payment, InvoiceView invoice, Instant validUntil) {}

  record CouponView(String code, int percentOff, long amountOffXaf, Integer maxRedemptions, int redeemed, Instant validUntil, List<String> plans, boolean active,
      Instant createdAt) {}

  record WorkspaceEntitlements(Map<String, Long> entitlements, List<OverrideView> overrides) {}

  record DayCount(String day, long count) {}

  record PlatformMetrics(Instant generatedAt, Users users, List<DayCount> signups, Activation activation, List<DayCount> clicks, Resources resources,
      Database database, Worker worker, Events events) {
    public record Users(long total, long active, long pending, long suspended) {}

    public record Activation(long signups, long activated, double rate) {}

    public record Resources(long links, long qrCodes, long bioPages) {}

    public record Database(long beepBytes) {}

    public record Worker(Instant lastRollupAt, Long lagSeconds) {}

    public record Events(long queued, long dropped, long written) {}
  }

  record JobView(String name, String description, String schedule, Instant lastStartedAt, Instant lastFinishedAt, Boolean lastOk, String lastDetail,
      Instant lastSuccessAt, long runs24h, long failures24h) {}

  record InvitationView(UUID id, String email, String status, String invitedBy, Instant createdAt, Instant expiresAt, Instant usedAt) {}

  record InvitationsPage(List<InvitationView> items, String nextCursor) {}

  /** Le core ne répond pas : le back office reste utilisable en mode dégradé. */
  class CoreUnavailableException extends RuntimeException {
    public CoreUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Le core refuse la requête (400, 404, 409…) avec un code stable, relayé tel quel au front. */
  class CoreRejectedException extends RuntimeException {
    private final int status;
    private final String code;

    public CoreRejectedException(int status, String code) {
      super(status + " " + code);
      this.status = status;
      this.code = code;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }
  }

  record AbuseReport(UUID id,String url,String category,String detail,String source,String status,String reason,Instant createdAt,Instant resolvedAt) {}
  record Reports(List<AbuseReport> items,long open,long overdue) {}
  record BlockedHost(String host,String reason,Instant createdAt) {}
  record BlockedHosts(List<BlockedHost> items) {}
  record FeatureFlag(String key,boolean enabled,int rolloutPercent,int version,String reason) {}
  record FeatureFlags(List<FeatureFlag> items) {}
  default Reports reports(String status,int limit,int offset,UUID staff) {throw new UnsupportedOperationException();}
  default AbuseReport resolveReport(UUID id,String decision,String reason,UUID staff) {throw new UnsupportedOperationException();}
  default BlockedHosts blockedHosts(UUID staff) {throw new UnsupportedOperationException();}
  default BlockedHosts blockHost(String host,boolean blocked,String reason,UUID staff) {throw new UnsupportedOperationException();}
  default FeatureFlags featureFlags(UUID staff) {throw new UnsupportedOperationException();}
  default FeatureFlag publishFlag(String key,boolean enabled,int rollout,int version,String reason,UUID staff) {throw new UnsupportedOperationException();}
  default Map<String,Object> exportUser(UUID id,UUID staff) {throw new UnsupportedOperationException();}
  default void eraseUser(UUID id,String reason,UUID staff) {throw new UnsupportedOperationException();}

}
