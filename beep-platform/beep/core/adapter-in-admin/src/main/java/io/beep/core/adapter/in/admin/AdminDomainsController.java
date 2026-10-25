package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.AdminDomainsUseCase;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.domain.customdomain.CustomDomain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Domaines personnalisés côté staff : liste globale, état DNS/TLS, relance de la vérification, blocage. */
@RestController
@RequestMapping("/internal/admin/v1/domains")
@Profile("!worker & !migrate")
public class AdminDomainsController {
  record DomainView(UUID id, UUID workspaceId, String ownerEmail, String hostname, String dnsStatus, String tlsStatus, boolean isDefault, boolean blocked, String blockedReason,
      String lastError, long links, Instant verifiedAt, Instant lastCheckAt, Instant createdAt) {}

  record DomainsPage(List<DomainView> items, String nextCursor) {}

  record Reason(String reason) {}

  private final AdminDomainsUseCase domains;

  public AdminDomainsController(AdminDomainsUseCase domains) {
    this.domains = domains;
  }

  @GetMapping
  DomainsPage list(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    CustomDomainRepository.AdminPage page = domains.page(q, status, limit, cursor);
    return new DomainsPage(page.items().stream().map(AdminDomainsController::view).toList(), page.nextCursor());
  }

  @GetMapping("/{id}")
  DomainView get(@PathVariable UUID id) {
    return view(domains.get(id));
  }

  @PostMapping("/{id}/recheck")
  DomainView recheck(@PathVariable UUID id) {
    return view(domains.recheck(id));
  }

  @PostMapping(value = "/{id}/block", consumes = MediaType.APPLICATION_JSON_VALUE)
  DomainView block(@PathVariable UUID id, @RequestBody Reason r) {
    return view(domains.block(id, r.reason()));
  }

  @PostMapping("/{id}/unblock")
  DomainView unblock(@PathVariable UUID id) {
    return view(domains.unblock(id));
  }

  private static DomainView view(CustomDomainRepository.AdminRow row) {
    CustomDomain d = row.domain();
    return new DomainView(d.id(), d.workspaceId(), row.ownerEmail(), d.hostname(), d.dnsStatus().db(), d.tlsStatus().db(), d.isDefault(), d.blocked(), d.blockedReason(),
        d.lastError(), row.linkCount(), d.verifiedAt(), d.lastCheckAt(), d.createdAt());
  }
}
