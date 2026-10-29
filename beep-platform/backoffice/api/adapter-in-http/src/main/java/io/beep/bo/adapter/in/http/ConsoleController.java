package io.beep.bo.adapter.in.http;

import io.beep.bo.app.audit.AuditLogUseCase;
import io.beep.bo.app.links.LinksModerationUseCase;
import io.beep.bo.app.ops.OperationsUseCase;
import io.beep.bo.app.plans.EntitlementsUseCase;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.users.UserAdminUseCase;
import io.beep.bo.domain.audit.AuditChain;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Modules de la phase 1 : fiche utilisateur, liens, plans & droits, métriques, jobs, journal d'audit. */
@RestController
@RequestMapping("/api")
@Profile("!migrate")
public class ConsoleController {
  record StatusRequest(String status, String reason, String totp) {}

  record ReasonRequest(String reason) {}

  record OverridesRequest(Map<String, Long> overrides, String reason, String totp) {}

  record ReservedSlugs(List<String> items) {}

  record Plans(List<CoreGatewayPort.PlanView> items) {}

  record Jobs(List<CoreGatewayPort.JobView> items) {}

  record AuditEntry(long seq, Instant at, String actorEmail, String action, String target, String detail, String ip, String hash) {}

  record AuditPage(List<AuditEntry> items, Long nextBefore, boolean intact, long brokenAt, long total) {}

  private final UserAdminUseCase users;
  private final LinksModerationUseCase links;
  private final EntitlementsUseCase entitlements;
  private final OperationsUseCase operations;
  private final AuditLogUseCase auditLog;
  private final StaffSessions sessions;

  public ConsoleController(UserAdminUseCase users, LinksModerationUseCase links, EntitlementsUseCase entitlements, OperationsUseCase operations,
      AuditLogUseCase auditLog, StaffSessions sessions) {
    this.users = users;
    this.links = links;
    this.entitlements = entitlements;
    this.operations = operations;
    this.auditLog = auditLog;
    this.sessions = sessions;
  }

  // ---- Utilisateurs ----
  @GetMapping("/users/{id}")
  CoreGatewayPort.UserDetail user(@PathVariable UUID id, HttpServletRequest req) {
    return users.detail(sessions.require(req), id, req.getRemoteAddr());
  }

  @PostMapping(value = "/users/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.UserView status(@PathVariable UUID id, @RequestBody StatusRequest r, HttpServletRequest req) {
    return users.setStatus(sessions.require(req), id, r.status(), r.reason(), r.totp(), req.getRemoteAddr());
  }

  // ---- Liens ----
  @GetMapping("/links")
  CoreGatewayPort.LinksPage links(@RequestParam(required = false) String q, @RequestParam(required = false) UUID workspaceId,
      @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor, HttpServletRequest req) {
    return links.search(sessions.require(req), q, workspaceId, limit, cursor, req.getRemoteAddr());
  }

  @GetMapping("/links/{id}")
  CoreGatewayPort.LinkView link(@PathVariable UUID id, HttpServletRequest req) {
    return links.get(sessions.require(req), id, req.getRemoteAddr());
  }

  @PostMapping(value = "/links/{id}/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.LinkView disable(@PathVariable UUID id, @RequestBody ReasonRequest r, HttpServletRequest req) {
    return links.disable(sessions.require(req), id, r.reason(), req.getRemoteAddr());
  }

  @PostMapping(value = "/links/{id}/enable", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.LinkView enable(@PathVariable UUID id, HttpServletRequest req) {
    return links.enable(sessions.require(req), id, req.getRemoteAddr());
  }

  @GetMapping("/reserved-slugs")
  ReservedSlugs reserved(HttpServletRequest req) {
    return new ReservedSlugs(links.reservedSlugs(sessions.require(req)));
  }

  // ---- Plans & droits ----
  @GetMapping("/plans")
  Plans plans(HttpServletRequest req) {
    return new Plans(entitlements.plans(sessions.require(req)));
  }

  @PutMapping(value = "/workspaces/{id}/entitlements", consumes = MediaType.APPLICATION_JSON_VALUE)
  CoreGatewayPort.WorkspaceEntitlements overrides(@PathVariable UUID id, @RequestBody OverridesRequest r, HttpServletRequest req) {
    return entitlements.setOverrides(sessions.require(req), id, r.overrides(), r.reason(), r.totp(), req.getRemoteAddr());
  }

  // ---- Exploitation ----
  @GetMapping("/metrics")
  OperationsUseCase.Metrics metrics(@RequestParam(defaultValue = "30") int days, HttpServletRequest req) {
    return operations.metrics(sessions.require(req), days);
  }

  @GetMapping("/jobs")
  Jobs jobs(HttpServletRequest req) {
    return new Jobs(operations.jobs(sessions.require(req)));
  }

  // ---- Journal d'audit ----
  @GetMapping("/audit")
  AuditPage audit(@RequestParam(required = false) Long before, @RequestParam(required = false) String action, @RequestParam(required = false) String actor,
      @RequestParam(defaultValue = "50") int limit, HttpServletRequest req) {
    AuditLogUseCase.View v = auditLog.execute(sessions.require(req), limit, before, action, actor, req.getRemoteAddr());
    return new AuditPage(v.page().items().stream().map(ConsoleController::entry).toList(), v.page().nextBeforeSeq(), v.intact(), v.brokenAt(), v.total());
  }

  private static AuditEntry entry(AuditChain.Entry e) {
    return new AuditEntry(e.seq(), e.event().at(), e.event().actorEmail(), e.event().action(), e.event().target(), e.event().detail(), e.event().ip(), e.hash());
  }
}
