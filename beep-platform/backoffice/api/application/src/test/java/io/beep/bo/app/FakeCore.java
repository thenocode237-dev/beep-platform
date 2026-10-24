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
  public List<PlanView> plans(UUID staffId) { return List.of(new PlanView("starter", "Starter", 1, 0, true, Map.of("links.stock_max", 5L))); }
  public WorkspaceEntitlements setEntitlements(UUID ws, Map<String, Long> overrides, String reason, UUID staffId) {
    calls.add("entitlements:" + overrides);
    return new WorkspaceEntitlements(Map.of("links.stock_max", 55L), List.of());
  }
  public PlatformMetrics metrics(int days, UUID staffId) { return null; }
  public List<JobView> jobs(UUID staffId) { return List.of(); }
  public InvitationsPage invitations(int limit, String cursor, UUID staffId) { return new InvitationsPage(List.of(), null); }
  public InvitationView invite(String email, String locale, UUID staffId) {
    calls.add("invite:" + email);
    return new InvitationView(UUID.randomUUID(), email, "pending", String.valueOf(staffId), T, T.plusSeconds(86400 * 30), null);
  }
  public String contractVersion() { return "0.2.0"; }
  public boolean isReachable() { return true; }

  static UserView user(String status) {
    return new UserView(USER, "ada@example.com", status, true, false, T);
  }
}
