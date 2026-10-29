package io.beep.bo.app;

import io.beep.bo.app.port.CoreGatewayPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Doublure du core : réponses fixes, appels enregistrés (pour vérifier que l'action a bien été transmise, avec quel staff). */
class FakeCore implements CoreGatewayPort {
  final List<String> calls = new ArrayList<>();
  static final Instant T = Instant.parse("2026-01-01T10:00:00Z");
  static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
  static final UUID LINK = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

  LinkView link(String status, String reason) {
    return new LinkView(LINK, "promo", "https://shop.example", null, status, reason, UUID.randomUUID(), "ada@example.com", T, T);
  }

  public UsersPage listUsers(int limit, String cursor, String query, UUID staffId) { calls.add("listUsers:" + query); return new UsersPage(List.of(), null); }
  public UserDetail getUser(UUID id, UUID staffId) { calls.add("getUser"); return new UserDetail(user("active"), null, List.of(), Map.of(), List.of()); }
  public UserView setUserStatus(UUID id, String status, String reason, UUID staffId) { calls.add("status:" + status + ":" + staffId); return user(status); }
  public LinksPage listLinks(String query, UUID ws, int limit, String cursor, UUID staffId) { calls.add("links:" + query); return new LinksPage(List.of(link("active", null)), null); }
  public LinkView getLink(UUID id, UUID staffId) { return link("active", null); }
  public LinkView disableLink(UUID id, String reason, UUID staffId) { calls.add("disable:" + reason); return link("disabled", reason); }
  public LinkView enableLink(UUID id, UUID staffId) { calls.add("enable"); return link("active", null); }
  public List<String> reservedSlugs(UUID staffId) { return List.of("app", "login"); }
  public List<PlanView> plans(UUID staffId) { return List.of(new PlanView("pro", "Pro", liveVersion, 4999, 13500, 41990, false, Map.of("links.stock_max", 50L)), new PlanView("starter", "Starter", 1, 0, 0, 0, true, Map.of("links.stock_max", 5L))); }
  public WorkspaceEntitlements setEntitlements(UUID ws, Map<String, Long> overrides, String reason, UUID staffId) {
    calls.add("entitlements:" + overrides);
    return new WorkspaceEntitlements(Map.of("links.stock_max", 55L), List.of());
  }
  public PlatformMetrics metrics(int days, UUID staffId) { return null; }
  public List<JobView> jobs(UUID staffId) { return List.of(); }
  public BillingSummary billingSummary(UUID staffId) { return new BillingSummary(0, 1, 0, 0, 4999, 59988, 4999, 0, 0); }
  public List<SubscriptionView> subscriptions(String status, int limit, int offset, UUID staffId) { return List.of(); }
  public List<SubscriptionView> upcomingRenewals(int days, int limit, UUID staffId) { return List.of(); }
  public PaymentsPage payments(String status, UUID ws, int limit, String cursor, UUID staffId) { return new PaymentsPage(List.of(), null); }
  public List<InvoiceView> workspaceInvoices(UUID ws, UUID staffId) { return List.of(); }
  public byte[] invoicePdf(String number, UUID staffId) { calls.add("invoicePdf:" + number); return new byte[] {1}; }
  public ManualPaymentResult recordManualPayment(UUID ws, String plan, String cycle, long amountXaf, String reference, String country, UUID staffId) {
    calls.add("manual:" + plan + ":" + amountXaf + ":" + reference);
    return new ManualPaymentResult(null, new InvoiceView("BEEP-2026-000001", UUID.randomUUID(), "Pro", cycle, amountXaf, amountXaf, 0, 0, "CM", T), T.plusSeconds(86400 * 30));
  }
  public SubscriptionView changePlan(UUID ws, String plan, Instant until, String reason, UUID staffId) {
    calls.add("changePlan:" + plan + ":" + reason);
    return new SubscriptionView(ws, null, null, plan, "active", null, T, T, T, 0);
  }
  public List<CouponView> coupons(UUID staffId) { return List.of(); }
  public CouponView createCoupon(String code, Integer percentOff, Long amountOffXaf, Integer maxRedemptions, Instant validUntil, List<String> plans, UUID staffId) {
    calls.add("coupon:" + code);
    return new CouponView(code, percentOff == null ? 0 : percentOff, amountOffXaf == null ? 0 : amountOffXaf, maxRedemptions, 0, validUntil, plans, true, T);
  }
  public CouponView setCouponActive(String code, boolean active, UUID staffId) { calls.add("couponActive:" + code + ":" + active); return new CouponView(code, 10, 0, null, 0, null, List.of(), active, T); }
  public int liveVersion = 1;
  public PlanView publishPlan(String code, String name, long price, Long q, Long a, boolean isPublic, Map<String, Long> entitlements, UUID staffId) {
    calls.add("publish:" + code + ":" + price);
    liveVersion++;
    return new PlanView(code, name, liveVersion, price, q == null ? 0 : q, a == null ? 0 : a, isPublic, entitlements);
  }
  public DomainView domainView(String status, boolean blocked) {
    return new DomainView(UUID.randomUUID(), UUID.randomUUID(), "ada@example.com", "go.acme.co", status, "pending", false, blocked, blocked ? "abus" : null, null, 3, T, T, T);
  }
  public DomainsPage domains(String query, String status, int limit, String cursor, UUID staffId) { return new DomainsPage(List.of(domainView("verified", false)), null); }
  public DomainView domain(UUID id, UUID staffId) { return domainView("verified", false); }
  public DomainView recheckDomain(UUID id, UUID staffId) { calls.add("recheckDomain"); return domainView("verified", false); }
  public DomainView blockDomain(UUID id, String reason, UUID staffId) { calls.add("blockDomain:" + reason); return domainView("verified", true); }
  public DomainView unblockDomain(UUID id, UUID staffId) { calls.add("unblockDomain"); return domainView("verified", false); }
  public List<AppView> catalog(UUID staffId) { return List.of(new AppView("youtube", "YouTube", List.of("youtube.com"), "youtube://x", "com.google.android.youtube", null, true, 1)); }
  public AppView publishApp(String code, String name, List<String> hosts, String ios, String pkg, String android, boolean enabled, UUID staffId) {
    calls.add("publishApp:" + code);
    return new AppView(code, name, hosts, ios, pkg, android, enabled, 2);
  }
  public List<AppView> setAppEnabled(String code, boolean enabled, UUID staffId) { calls.add("appEnabled:" + code + ":" + enabled); return catalog(staffId); }
  public AppTestResult testApp(String url, String profile, AppView candidate, UUID staffId) { return new AppTestResult(profile, true, "YouTube", "web", url, null, null, "ios", false, "ok"); }
  public String contractVersion() { return "0.5.0"; }
  public boolean isReachable() { return true; }

  static UserView user(String status) {
    return new UserView(USER, "ada@example.com", status, true, false, T);
  }
}
