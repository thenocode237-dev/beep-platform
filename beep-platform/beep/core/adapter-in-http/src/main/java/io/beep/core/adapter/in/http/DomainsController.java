package io.beep.core.adapter.in.http;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.domains.CustomDomainsUseCase;
import io.beep.core.domain.customdomain.CustomDomain;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Domaines personnalisés de l'espace client (/v1/domains) : ajout, instructions DNS, vérification, défaut, page racine et chemins inconnus. */
@RestController
@RequestMapping("/v1/domains")
@Profile("!worker & !migrate")
public class DomainsController {
  record DomainRequest(String hostname) {}

  record SettingsRequest(Boolean makeDefault, String rootRedirectUrl, String notFoundUrl) {}

  record DnsInstructions(String txtName, String txtValue, String cnameTarget) {}

  record DomainView(UUID id, String hostname, String dnsStatus, String tlsStatus, boolean isDefault, boolean blocked, String blockedReason, String lastError,
      String rootRedirectUrl, String notFoundUrl, long links, Instant verifiedAt, Instant lastCheckAt, Instant createdAt, DnsInstructions dns) {}

  record DomainsList(List<DomainView> items, DnsInstructions targets) {}

  private final CustomDomainsUseCase domains;
  private final SessionCookies cookies;
  private final String cnameTarget;

  public DomainsController(CustomDomainsUseCase domains, SessionCookies cookies, PublicSite site, @Value("${beep.domains.cname-target}") String cnameTarget) {
    this.domains = domains;
    this.cookies = cookies;
    this.cnameTarget = cnameTarget.isBlank() ? "domains." + site.host() : cnameTarget;
  }

  @GetMapping
  DomainsList list(HttpServletRequest req) {
    return new DomainsList(domains.list(cookies.requireUser(req)).stream().map(v -> view(v.domain(), v.linkCount())).toList(), new DnsInstructions(null, null, cnameTarget));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<DomainView> add(@RequestBody DomainRequest r, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(view(domains.add(cookies.requireUser(req), r.hostname()), 0));
  }

  @PostMapping("/{id}/verify")
  DomainView verify(@PathVariable UUID id, HttpServletRequest req) {
    return view(domains.verify(cookies.requireUser(req), id), -1);
  }

  @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  DomainView update(@PathVariable UUID id, @RequestBody SettingsRequest r, HttpServletRequest req) {
    return view(domains.update(cookies.requireUser(req), id, new CustomDomainsUseCase.Settings(r.makeDefault(), r.rootRedirectUrl(), r.notFoundUrl())), -1);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest req) {
    domains.delete(cookies.requireUser(req), id);
    return ResponseEntity.noContent().build();
  }

  private DomainView view(CustomDomain d, long links) {
    return new DomainView(d.id(), d.hostname(), d.dnsStatus().db(), d.tlsStatus().db(), d.isDefault(), d.blocked(), d.blockedReason(), d.lastError(), d.rootRedirectUrl(),
        d.notFoundUrl(), links, d.verifiedAt(), d.lastCheckAt(), d.createdAt(), new DnsInstructions(d.txtName(), d.verifyToken(), cnameTarget));
  }
}
