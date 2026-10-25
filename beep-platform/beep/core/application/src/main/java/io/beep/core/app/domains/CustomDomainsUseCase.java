package io.beep.core.app.domains;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.DnsVerifierPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.customdomain.Hostname;
import io.beep.core.domain.plan.EntitlementKeys;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Domaines personnalisés de l'espace client : ajout (quota `domains.max`), vérification DNS, domaine par défaut, page racine et chemins inconnus,
 * suppression. Un domaine ne sert des liens qu'une fois le DNS vérifié ; un domaine qui porte encore des liens ne peut pas être supprimé.
 */
public class CustomDomainsUseCase {
  public record View(CustomDomain domain, long linkCount) {}

  /** Champs absents (null) inchangés ; chaîne vide = effacement. */
  public record Settings(Boolean makeDefault, String rootRedirectUrl, String notFoundUrl) {}

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final CustomDomainRepository domains;
  private final DnsVerifierPort dns;
  private final TokenPort tokens;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;

  public CustomDomainsUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, CustomDomainRepository domains, DnsVerifierPort dns, TokenPort tokens,
      RateLimiterPort limiter, TransactionPort tx, PublicSite site, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.domains = domains;
    this.dns = dns;
    this.tokens = tokens;
    this.limiter = limiter;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public List<View> list(User actor) {
    Workspace w = workspaces.ensureFor(actor);
    return domains.forWorkspace(w.id()).stream().map(d -> new View(d, domains.linkCount(d.id()))).toList();
  }

  public CustomDomain add(User actor, String rawHostname) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    String hostname = Hostname.parse(rawHostname, site.host());
    return tx.run(() -> {
      workspaceRepository.lock(ctx.id());
      long limit = ctx.entitlements().value(EntitlementKeys.DOMAINS_MAX).orElse(0);
      if (limit != -1 && domains.count(ctx.id()) >= limit) throw new DomainException("quota_exceeded");
      CustomDomain domain = CustomDomain.pending(UUID.randomUUID(), ctx.id(), hostname, verifyToken(), clock.instant());
      try {
        domains.insert(domain);
      } catch (CustomDomainRepository.DuplicateHostnameException e) {
        throw new DomainException("domain_taken");
      }
      return domain;
    });
  }

  /** Relance la vérification DNS (limitée : les résolveurs publics ne sont pas à nous). */
  public CustomDomain verify(User actor, UUID id) {
    Workspace w = workspaces.ensureFor(actor);
    CustomDomain d = owned(w, id);
    if (!limiter.tryAcquire("dns-verify:" + w.id(), 20, Duration.ofMinutes(10))) throw new DomainException("rate_limited");
    return recheck(d);
  }

  /** Utilisé aussi par le staff : mise à jour de l'état selon le résultat DNS. */
  public CustomDomain recheck(CustomDomain d) {
    DnsVerifierPort.Result result = dns.check(d.hostname(), d.verifyToken());
    return tx.run(() -> {
      CustomDomain current = domains.lockById(d.id()).orElseThrow(() -> new DomainException("not_found"));
      // Une résolution impossible (réseau) ne change pas l'état : ni validation ni invalidation sur une panne de DNS.
      CustomDomain next = result.unreachable() ? current : current.checked(result.ok(), result.error(), clock.instant());
      if (result.unreachable()) return current;
      domains.update(next);
      return next;
    });
  }

  public CustomDomain update(User actor, UUID id, Settings s) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    return tx.run(() -> {
      workspaceRepository.lock(ctx.id());
      CustomDomain d = domains.lockById(id).filter(x -> x.workspaceId().equals(ctx.id())).orElseThrow(() -> new DomainException("not_found"));
      if (s.rootRedirectUrl() != null || s.notFoundUrl() != null) {
        d = d.withRedirects(s.rootRedirectUrl() != null ? s.rootRedirectUrl() : d.rootRedirectUrl(), s.notFoundUrl() != null ? s.notFoundUrl() : d.notFoundUrl());
      }
      if (s.makeDefault() != null) {
        d = d.withDefault(s.makeDefault());
        domains.setDefault(ctx.id(), s.makeDefault() ? d.id() : null);
      }
      domains.update(d);
      return d;
    });
  }

  public void delete(User actor, UUID id) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    tx.run(() -> {
      workspaceRepository.lock(ctx.id());
      CustomDomain d = domains.lockById(id).filter(x -> x.workspaceId().equals(ctx.id())).orElseThrow(() -> new DomainException("not_found"));
      if (domains.linkCount(d.id()) > 0) throw new DomainException("domain_in_use");
      domains.delete(d.id());
    });
  }

  /** Jeton de propriété : 32 caractères du jeton aléatoire (assez court pour un TXT, 190 bits). */
  private String verifyToken() {
    String t = tokens.newToken();
    return t.length() > 32 ? t.substring(0, 32) : t;
  }

  private CustomDomain owned(Workspace w, UUID id) {
    return domains.findById(id).filter(d -> d.workspaceId().equals(w.id())).orElseThrow(() -> new DomainException("not_found"));
  }
}
