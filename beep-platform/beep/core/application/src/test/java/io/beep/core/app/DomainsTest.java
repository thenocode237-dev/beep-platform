package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.admin.AdminDomainsUseCase;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.domains.CustomDomainsUseCase;
import io.beep.core.app.domains.TlsAllowUseCase;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.DnsVerifierPort;
import io.beep.core.app.port.DomainRouterPort;
import io.beep.core.app.redirect.Outcome;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.customdomain.DnsStatus;
import io.beep.core.domain.customdomain.Hostname;
import io.beep.core.domain.customdomain.TlsStatus;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Domaines personnalisés : nom d'hôte, quota, vérification DNS, liens par domaine, résolution par espace de noms, TLS à la demande, blocage. */
class DomainsTest {
  static final Plan PRO = new Plan("pro", "Pro", 1, 4999, true, Map.of("links.stock_max", 50L, "domains.max", 1L));

  Platform p;
  MemDomains domains;
  Dns dns;
  Router router;
  CustomDomainsUseCase uc;
  CreateLinkUseCase create;
  User ada;

  @BeforeEach
  void setUp() {
    p = new Platform();
    p.plans.published.put("pro", PRO);
    domains = new MemDomains();
    domains.links = () -> p.links.byId.values();
    dns = new Dns();
    router = new Router();
    uc = new CustomDomainsUseCase(p.workspaces, p.workspaceRepo, domains, dns, p.tokenGen, p.limiter, p.tx, p.site, p.clock);
    create = new CreateLinkUseCase(p.workspaces, p.workspaceRepo, p.links, p.tx, p.site, p.clock, domains);
    ada = p.verifiedUser("ada@example.com");
    p.workspaces.ensureFor(ada);
    p.workspaceRepo.updatePlan(p.workspaces.ensureFor(ada).id(), "pro");
  }

  // ---- Nom d'hôte ----
  @Test
  void hostnamesAreNormalizedAndStrictlyValidated() {
    assertThat(Hostname.parse(" HTTPS://Go.Acme.Co/ ", "beep.example")).isEqualTo("go.acme.co");
    for (String bad : new String[] {"", "localhost", "10.0.0.1", "acme", "-a.co", "a..co", "*.acme.co", "acme.co:8080", "ac me.co", "a.123", "x".repeat(64) + ".co"}) {
      assertThatThrownBy(() -> Hostname.parse(bad, "beep.example")).as(bad).hasMessage("invalid_hostname");
    }
    assertThatThrownBy(() -> Hostname.parse("beep.example", "beep.example")).hasMessage("reserved_hostname");
    assertThatThrownBy(() -> Hostname.parse("dashboard.beep.example", "beep.example")).hasMessage("reserved_hostname");
    assertThatThrownBy(() -> Hostname.parse("app.beep.localhost", "beep.example")).hasMessage("reserved_hostname");
  }

  // ---- Cycle de vie ----
  @Test
  void addingADomainRespectsTheQuotaAndUniqueness() {
    User eve = p.verifiedUser("eve@example.com");
    assertThatThrownBy(() -> uc.add(eve, "go.eve.co")).hasMessage("quota_exceeded"); // Starter : domains.max = 0

    CustomDomain d = uc.add(ada, "go.acme.co");
    assertThat(d.dnsStatus()).isEqualTo(DnsStatus.PENDING);
    assertThat(d.verifyToken()).isNotBlank();
    assertThat(d.txtName()).isEqualTo("_beep-verify.go.acme.co");
    assertThatThrownBy(() -> uc.add(ada, "other.acme.co")).hasMessage("quota_exceeded");

    p.workspaceRepo.updatePlan(p.workspaces.ensureFor(eve).id(), "pro");
    assertThatThrownBy(() -> uc.add(eve, "GO.acme.co")).hasMessage("domain_taken");
  }

  @Test
  void dnsVerificationNeedsOwnershipAndRoutingAndSurvivesOutages() {
    CustomDomain d = uc.add(ada, "go.acme.co");

    dns.result = new DnsVerifierPort.Result(false, false, false);
    assertThat(uc.verify(ada, d.id()).lastError()).isEqualTo("txt_missing");
    assertThat(uc.verify(ada, d.id()).dnsStatus()).isEqualTo(DnsStatus.FAILED);

    dns.result = new DnsVerifierPort.Result(true, false, false);
    assertThat(uc.verify(ada, d.id()).lastError()).isEqualTo("routing_missing");

    dns.result = new DnsVerifierPort.Result(true, true, false);
    CustomDomain ok = uc.verify(ada, d.id());
    assertThat(ok.dnsStatus()).isEqualTo(DnsStatus.VERIFIED);
    assertThat(ok.verifiedAt()).isNotNull();
    assertThat(ok.lastError()).isNull();

    // une panne du résolveur ne dé-vérifie rien
    dns.result = new DnsVerifierPort.Result(false, false, true);
    assertThat(uc.verify(ada, d.id()).dnsStatus()).isEqualTo(DnsStatus.VERIFIED);
    // un enregistrement supprimé après coup : le domaine reste servi, l'erreur est signalée
    dns.result = new DnsVerifierPort.Result(false, false, false);
    CustomDomain later = uc.verify(ada, d.id());
    assertThat(later.dnsStatus()).isEqualTo(DnsStatus.VERIFIED);
    assertThat(later.lastError()).isEqualTo("txt_missing");
  }

  @Test
  void aWorkspaceCannotTouchAnotherWorkspacesDomain() {
    CustomDomain d = uc.add(ada, "go.acme.co");
    User eve = p.verifiedUser("eve@example.com");
    assertThatThrownBy(() -> uc.verify(eve, d.id())).hasMessage("not_found");
    assertThatThrownBy(() -> uc.update(eve, d.id(), new CustomDomainsUseCase.Settings(null, "https://x.co", null))).hasMessage("not_found");
    assertThatThrownBy(() -> uc.delete(eve, d.id())).hasMessage("not_found");
    assertThat(uc.list(eve)).isEmpty();
  }

  @Test
  void settingsValidateRedirectsAndDefaultNeedsAVerifiedDomain() {
    CustomDomain d = uc.add(ada, "go.acme.co");
    assertThatThrownBy(() -> uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(true, null, null))).hasMessage("domain_not_verified");
    verified(d);
    assertThatThrownBy(() -> uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(null, "ftp://x.co", null))).hasMessage("invalid_destination");
    assertThatThrownBy(() -> uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(null, "https://go.acme.co/loop", null))).hasMessage("destination_loop");
    CustomDomain s = uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(true, "https://acme.co", "https://acme.co/404"));
    assertThat(s.isDefault()).isTrue();
    assertThat(s.rootRedirectUrl()).isEqualTo("https://acme.co");
    assertThat(uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(null, "", null)).rootRedirectUrl()).isNull();
  }

  @Test
  void aDomainWithLinksCannotBeDeleted() {
    CustomDomain d = verified(uc.add(ada, "go.acme.co"));
    Link l = create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "promo", null, d.id().toString()));
    assertThatThrownBy(() -> uc.delete(ada, d.id())).hasMessage("domain_in_use");
    p.deleteLink.execute(ada, l.id());
    uc.delete(ada, d.id());
    assertThat(domains.byId).isEmpty();
  }

  // ---- Liens par domaine ----
  @Test
  void linksAreCreatedOnTheChosenOrDefaultDomainAndSlugsAreUniquePerDomain() {
    CustomDomain d = verified(uc.add(ada, "go.acme.co"));
    Link onMain = create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "promo", null));
    assertThat(onMain.domainId()).isNull();

    Link onDomain = create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/2", "promo", null, d.id().toString()));
    assertThat(onDomain.domainId()).isEqualTo(d.id()); // même slug sur un autre espace de noms
    assertThat(onDomain.registryKey()).isEqualTo(d.id() + "/promo");
    assertThatThrownBy(() -> create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/3", "promo", null, d.id().toString()))).hasMessage("slug_taken");
    assertThatThrownBy(() -> create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/3", "promo", null, "main"))).hasMessage("slug_taken");

    uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(true, null, null));
    assertThat(create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/4", "defaulted", null)).domainId()).isEqualTo(d.id());
    assertThat(create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/5", "onmain", null, "main")).domainId()).isNull();
  }

  @Test
  void aLinkCannotBeCreatedOnAnUnverifiedOrForeignDomain() {
    CustomDomain pending = uc.add(ada, "go.acme.co");
    assertThatThrownBy(() -> create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "abc1", null, pending.id().toString()))).hasMessage("domain_not_verified");
    assertThatThrownBy(() -> create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "abc2", null, UUID.randomUUID().toString()))).hasMessage("not_found");
    assertThatThrownBy(() -> create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "abc3", null, "pas-un-uuid"))).hasMessage("invalid_request");
    User eve = p.verifiedUser("eve@example.com");
    verified(pending);
    assertThatThrownBy(() -> create.execute(eve, new CreateLinkUseCase.Command("https://shop.example", "abc4", null, pending.id().toString()))).hasMessage("not_found");
  }

  @Test
  void shortUrlsUseTheCustomDomain() {
    CustomDomain d = verified(uc.add(ada, "go.acme.co"));
    Link l = create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "promo", null, d.id().toString()));
    assertThat(p.site.shortUrl(l)).isEqualTo("https://go.acme.co/promo");
    assertThat(p.site.shortUrl(create.execute(ada, new CreateLinkUseCase.Command("https://shop.example", "plain", null, "main")))).isEqualTo("https://beep.example/plain");
  }

  // ---- Redirection par espace de noms ----
  @Test
  void aLinkIsOnlyServedOnItsOwnDomain() {
    CustomDomain d = verified(uc.add(ada, "go.acme.co"));
    create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/custom", "promo", null, d.id().toString()));
    create.execute(ada, new CreateLinkUseCase.Command("https://shop.example/main", "only-main", null, "main"));
    Visit visit = new Visit("203.0.113.1", "Mozilla/5.0", null, "fr", null, true, p.now);

    assertThat(p.resolveSlug.execute("promo", d.id(), visit)).isEqualTo(new Outcome.Redirect("https://shop.example/custom"));
    assertThat(p.resolveSlug.execute("promo", visit)).isInstanceOf(Outcome.NotFound.class); // pas sur le domaine de Beep
    assertThat(p.resolveSlug.execute("only-main", d.id(), visit)).isInstanceOf(Outcome.NotFound.class); // pas sur le domaine du client
    assertThat(p.resolveSlug.execute("only-main", visit)).isEqualTo(new Outcome.Redirect("https://shop.example/main"));
  }

  // ---- TLS à la demande ----
  @Test
  void certificatesAreOnlyIssuedForVerifiedUnblockedDomains() {
    var tls = new TlsAllowUseCase(router, domains, p.tx);
    CustomDomain d = uc.add(ada, "go.acme.co");
    assertThat(tls.allowed("go.acme.co")).isFalse(); // DNS non vérifié
    assertThat(tls.allowed("inconnu.acme.co")).isFalse();
    assertThat(tls.allowed("beep.example")).isFalse(); // le domaine de Beep n'est pas un domaine personnalisé
    assertThat(tls.allowed(null)).isFalse();
    assertThat(tls.allowed("x".repeat(300))).isFalse();

    verified(d);
    assertThat(tls.allowed("GO.acme.co")).isTrue();
    assertThat(domains.byId.get(d.id()).tlsStatus()).isEqualTo(TlsStatus.ACTIVE);
  }

  // ---- Staff ----
  @Test
  void blockingADomainStopsServingAndRemovesTheDefault() {
    var admin = new AdminDomainsUseCase(domains, uc, p.tx);
    var tls = new TlsAllowUseCase(router, domains, p.tx);
    CustomDomain d = verified(uc.add(ada, "go.acme.co"));
    uc.update(ada, d.id(), new CustomDomainsUseCase.Settings(true, null, null));

    assertThatThrownBy(() -> admin.block(d.id(), "")).hasMessage("invalid_reason");
    var row = admin.block(d.id(), "hameçonnage signalé");
    assertThat(row.domain().blocked()).isTrue();
    assertThat(row.domain().isDefault()).isFalse();
    assertThat(row.domain().blockedReason()).isEqualTo("hameçonnage signalé");
    assertThat(tls.allowed("go.acme.co")).isFalse();
    assertThat(router.route("go.acme.co").blocked()).isTrue();

    admin.unblock(d.id());
    assertThat(tls.allowed("go.acme.co")).isTrue();
    assertThatThrownBy(() -> admin.page(null, "weird", 20, null)).hasMessage("invalid_request");
  }

  @Test
  void staffRecheckUpdatesTheDnsState() {
    var admin = new AdminDomainsUseCase(domains, uc, p.tx);
    CustomDomain d = uc.add(ada, "go.acme.co");
    dns.result = new DnsVerifierPort.Result(true, true, false);
    assertThat(admin.recheck(d.id()).domain().dnsStatus()).isEqualTo(DnsStatus.VERIFIED);
  }

  // ---- Aides ----
  CustomDomain verified(CustomDomain d) {
    CustomDomain v = d.checked(true, null, p.now);
    domains.byId.put(v.id(), v);
    return v;
  }

  class Dns implements DnsVerifierPort {
    Result result = new Result(true, true, false);

    public Result check(String hostname, String token) { return result; }
  }

  /** Routeur de test : lit directement les domaines en mémoire. */
  class Router implements DomainRouterPort {
    public Route route(String host) {
      if ("beep.example".equals(host)) return Route.MAIN;
      return domains.findByHostname(host).map(d -> {
        boolean suspended = p.workspaceRepo.findById(d.workspaceId()).map(w -> w.suspended()).orElse(false);
        boolean blocked = d.blocked() || suspended;
        return new Route(Route.Kind.CUSTOM, d.id(), d.verified() && !blocked, blocked, d.rootRedirectUrl(), d.notFoundUrl());
      }).orElse(Route.UNKNOWN);
    }
  }

  static class MemDomains implements CustomDomainRepository {
    final Map<UUID, CustomDomain> byId = new LinkedHashMap<>();

    public void insert(CustomDomain d) {
      if (findByHostname(d.hostname()).isPresent()) throw new DuplicateHostnameException(null);
      byId.put(d.id(), d);
    }
    public Optional<CustomDomain> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<CustomDomain> findByHostname(String h) { return byId.values().stream().filter(d -> d.hostname().equals(h)).findFirst(); }
    public Optional<CustomDomain> lockById(UUID id) { return findById(id); }
    public void update(CustomDomain d) { byId.put(d.id(), d); }
    public void delete(UUID id) { byId.remove(id); }
    public List<CustomDomain> forWorkspace(UUID ws) { return byId.values().stream().filter(d -> d.workspaceId().equals(ws)).toList(); }
    public long count(UUID ws) { return forWorkspace(ws).size(); }
    public long linkCount(UUID id) { return links == null ? 0 : links.get().stream().filter(l -> id.equals(l.domainId())).count(); }
    public void setDefault(UUID ws, UUID id) {
      byId.replaceAll((k, d) -> d.workspaceId().equals(ws) && d.isDefault() && !k.equals(id) ? d.withDefault(false) : d);
    }
    public Optional<CustomDomain> defaultOf(UUID ws) { return forWorkspace(ws).stream().filter(CustomDomain::isDefault).findFirst(); }
    public Map<UUID, String> hostnames(Set<UUID> ids) {
      Map<UUID, String> out = new HashMap<>();
      ids.forEach(i -> findById(i).ifPresent(d -> out.put(i, d.hostname())));
      return out;
    }
    public AdminPage adminPage(String q, String status, int limit, String cursor) {
      return new AdminPage(new ArrayList<>(byId.values().stream().map(d -> new AdminRow(d, null, 0)).toList()), null);
    }
    public Optional<AdminRow> adminFind(UUID id) { return findById(id).map(d -> new AdminRow(d, null, linkCount(id))); }

    java.util.function.Supplier<java.util.Collection<Link>> links;
  }
}
