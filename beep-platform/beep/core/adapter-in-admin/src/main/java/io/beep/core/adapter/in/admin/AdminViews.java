package io.beep.core.adapter.in.admin;

import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Représentations du contrat contracts/beep-admin.openapi.yaml (noms et champs identiques au contrat). */
final class AdminViews {
  private AdminViews() {}

  record UserView(UUID id, String email, String status, boolean emailVerified, boolean totpEnabled, Instant createdAt) {}

  record WorkspaceView(UUID id, String name, String plan, String status, Instant createdAt, Instant firstLinkAt) {}

  record UsageView(String resource, long used, long limit, String period, String level) {}

  record OverrideView(String key, long value, String reason, Instant updatedAt, String updatedBy) {}

  record LinkView(UUID id, String slug, String destination, String title, String status, String disabledReason, UUID workspaceId, String ownerEmail,
      Instant createdAt, Instant updatedAt) {}

  static UserView user(User u) {
    return new UserView(u.id(), u.email().value(), u.status().db(), u.emailVerified(), u.totpEnabled(), u.createdAt());
  }

  static WorkspaceView workspace(Workspace w) {
    return w == null ? null : new WorkspaceView(w.id(), w.name(), w.planCode(), w.status().db(), w.createdAt(), w.firstLinkAt());
  }

  static UsageView usage(QuotaPolicy.Status s) {
    return new UsageView(s.resource().key(), s.used(), s.limit(), s.period().db(), s.level().db());
  }

  static OverrideView override(PlanRepository.EntitlementOverride o) {
    return new OverrideView(o.key(), o.value(), o.reason(), o.updatedAt(), o.updatedBy());
  }

  static LinkView link(LinkRepository.AdminRow row) {
    Link l = row.link();
    return new LinkView(l.id(), l.slug().value(), l.destination().url(), l.title(), l.status().db(), l.disabledReason(), l.workspaceId(), row.ownerEmail(),
        l.createdAt(), l.updatedAt());
  }

  static Map<String, Long> sorted(Map<String, Long> m) {
    return new TreeMap<>(m);
  }

  /** En-tête X-Staff-Id réduit aux caractères d'un UUID (il est journalisé et stocké). */
  static String staff(String header) {
    if (header == null) return null;
    String s = header.replaceAll("[^A-Za-z0-9-]", "");
    return s.isEmpty() ? null : s.substring(0, Math.min(s.length(), 36));
  }
}
