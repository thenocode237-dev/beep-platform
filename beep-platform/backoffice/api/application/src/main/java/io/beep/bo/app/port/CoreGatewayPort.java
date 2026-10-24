package io.beep.bo.app.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seul accès aux données métier de beep-core : l'API admin interne (contrat OpenAPI 0.2). Jamais la base de Beep.
 * Les DTO reprennent exactement les schémas du contrat.
 */
public interface CoreGatewayPort {
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

  record PlanView(String code, String name, int version, long priceXaf, boolean isPublic, Map<String, Long> entitlements) {}

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
}
