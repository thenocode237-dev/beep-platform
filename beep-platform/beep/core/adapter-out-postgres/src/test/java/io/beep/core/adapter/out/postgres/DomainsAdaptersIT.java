package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.DomainRouterPort;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.SlugResolverPort.Resolution;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.customdomain.DnsStatus;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Domaines personnalisés (phase 2) sur un PostgreSQL réel : dépôt, routage par hôte, slugs uniques par domaine, notifications de cache. */
class DomainsAdaptersIT {
  static final Instant NOW = Instant.parse("2026-03-10T10:00:00Z");
  static DSLContext dsl;
  static String url;
  static String user;
  static String password;

  @BeforeAll
  static void start() {
    PGSimpleDataSource pg = new PGSimpleDataSource();
    url = System.getenv("TEST_DATABASE_URL");
    if (url == null || url.isBlank()) {
      PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine");
      c.start();
      url = c.getJdbcUrl();
      user = c.getUsername();
      password = c.getPassword();
      pg.setUser(user);
      pg.setPassword(password);
    }
    pg.setUrl(url);
    Flyway flyway = Flyway.configure().dataSource(pg).cleanDisabled(false).load();
    flyway.clean();
    flyway.migrate();
    dsl = DSL.using(pg, SQLDialect.POSTGRES);
  }

  PostgresCustomDomainAdapter domains;
  PostgresLinkAdapter links;
  PostgresSlugResolverAdapter resolver;
  PostgresWorkspaceAdapter workspaces;
  Workspace ws;

  @BeforeEach
  void init() {
    dsl.execute("TRUNCATE users, slugs, custom_domains CASCADE");
    domains = new PostgresCustomDomainAdapter(dsl);
    links = new PostgresLinkAdapter(dsl);
    workspaces = new PostgresWorkspaceAdapter(dsl);
    resolver = new PostgresSlugResolverAdapter(dsl, new PostgresBioPageAdapter(dsl));
    ws = workspace("ada@example.com");
  }

  Workspace workspace(String email) {
    User u = User.register(UUID.randomUUID(), new Email(email), "h", "fr", NOW).verifyEmail(NOW);
    new PostgresUserAdapter(dsl).insert(u);
    return workspaces.ensure(Workspace.personal(UUID.randomUUID(), u.id(), "ada", NOW));
  }

  CustomDomain domain(String host) {
    CustomDomain d = CustomDomain.pending(UUID.randomUUID(), ws.id(), host, "token-" + host, NOW);
    domains.insert(d);
    return d;
  }

  Link link(String slug, CustomDomain d) {
    Link l = Link.create(UUID.randomUUID(), ws.id(), new Slug(slug), Destination.parse("https://shop.example/" + slug), null, NOW, d == null ? null : d.id(), null);
    links.insert(l);
    return l;
  }

  @Test
  void domainRoundTripWithUniqueHostnameAndSingleDefault() {
    CustomDomain a = domain("go.acme.co");
    assertThatThrownBy(() -> domains.insert(CustomDomain.pending(UUID.randomUUID(), ws.id(), "go.acme.co", "t", NOW))).isInstanceOf(CustomDomainRepository.DuplicateHostnameException.class);
    CustomDomain b = domain("link.acme.co");

    CustomDomain verified = a.checked(true, null, NOW);
    domains.update(verified);
    CustomDomain read = domains.findByHostname("go.acme.co").orElseThrow();
    assertThat(read.dnsStatus()).isEqualTo(DnsStatus.VERIFIED);
    assertThat(read.verifiedAt()).isEqualTo(NOW);
    assertThat(read.verifyToken()).isEqualTo("token-go.acme.co");

    domains.update(b.checked(true, null, NOW));
    domains.setDefault(ws.id(), a.id());
    domains.setDefault(ws.id(), b.id()); // un seul défaut à la fois
    assertThat(domains.defaultOf(ws.id())).get().extracting(CustomDomain::hostname).isEqualTo("link.acme.co");
    assertThat(domains.forWorkspace(ws.id())).hasSize(2);
    assertThat(domains.count(ws.id())).isEqualTo(2);
    assertThat(domains.hostnames(Set.of(a.id(), b.id()))).containsEntry(a.id(), "go.acme.co");
    domains.setDefault(ws.id(), null);
    assertThat(domains.defaultOf(ws.id())).isEmpty();
  }

  @Test
  void sameSlugCanExistOnTheMainDomainAndOnCustomDomains() {
    CustomDomain a = domain("go.acme.co");
    CustomDomain b = domain("link.acme.co");
    Link main = link("promo", null);
    Link onA = link("promo", a);
    Link onB = link("promo", b);
    assertThatThrownBy(() -> link("promo", a)).isInstanceOf(SlugRegistryPort.SlugTakenException.class);
    assertThatThrownBy(() -> link("promo", null)).isInstanceOf(SlugRegistryPort.SlugTakenException.class);

    assertThat(resolver.resolve("promo").targetId()).isEqualTo(main.id());
    assertThat(resolver.resolve(a.id() + "/promo").targetId()).isEqualTo(onA.id());
    assertThat(resolver.resolve(b.id() + "/promo").targetId()).isEqualTo(onB.id());
    assertThat(resolver.resolve(a.id() + "/other").kind()).isEqualTo(Resolution.Kind.NONE);

    // renommer et supprimer libèrent la bonne clé
    links.update(onA.edit(new Slug("promo2"), onA.destination(), null, NOW));
    assertThat(resolver.resolve(a.id() + "/promo").kind()).isEqualTo(Resolution.Kind.NONE);
    assertThat(resolver.resolve(a.id() + "/promo2").targetId()).isEqualTo(onA.id());
    assertThat(resolver.resolve("promo").targetId()).isEqualTo(main.id());
    links.delete(onA.id());
    assertThat(resolver.resolve(a.id() + "/promo2").kind()).isEqualTo(Resolution.Kind.NONE);
    link("promo", a); // la clé est de nouveau libre
  }

  @Test
  void linksCarryTheirDomainHostname() {
    CustomDomain a = domain("go.acme.co");
    Link onA = link("promo", a);
    link("plain", null);
    assertThat(links.findById(onA.id()).orElseThrow().domainHost()).isEqualTo("go.acme.co");
    var page = links.page(ws.id(), null, 10, null);
    assertThat(page.items()).extracting(Link::domainHost).containsExactlyInAnyOrder("go.acme.co", null);
    assertThat(domains.linkCount(a.id())).isEqualTo(1);
  }

  @Test
  void routerClassifiesHostsAndReflectsBlockingAndSuspension() {
    var router = new PostgresDomainRouterAdapter(dsl, Set.of("beep.click", "localhost"));
    CustomDomain a = domain("go.acme.co");
    assertThat(router.route("beep.click")).isEqualTo(DomainRouterPort.Route.MAIN);
    assertThat(router.route("LOCALHOST")).isEqualTo(DomainRouterPort.Route.MAIN);
    assertThat(router.route("nope.example").kind()).isEqualTo(DomainRouterPort.Route.Kind.UNKNOWN);

    var pending = router.route("go.acme.co");
    assertThat(pending.kind()).isEqualTo(DomainRouterPort.Route.Kind.CUSTOM);
    assertThat(pending.servable()).isFalse(); // DNS non vérifié

    a = a.checked(true, null, NOW).withRedirects("https://acme.co", "https://acme.co/404");
    domains.update(a);
    var ok = router.route("go.acme.co");
    assertThat(ok.servable()).isTrue();
    assertThat(ok.domainId()).isEqualTo(a.id());
    assertThat(ok.rootRedirectUrl()).isEqualTo("https://acme.co");

    domains.update(a.block("hameçonnage"));
    assertThat(router.route("go.acme.co").servable()).isFalse();
    assertThat(router.route("go.acme.co").blocked()).isTrue();

    domains.update(a);
    workspaces.updateStatus(ws.id(), WorkspaceStatus.SUSPENDED);
    assertThat(router.route("go.acme.co").blocked()).isTrue();
  }

  @Test
  void adminPageFiltersByHostnameAndStatus() {
    CustomDomain a = domain("go.acme.co");
    domain("shop.other.co");
    domains.update(a.checked(true, null, NOW));
    assertThat(domains.adminPage(null, null, 10, null).items()).hasSize(2);
    assertThat(domains.adminPage("acme", null, 10, null).items()).singleElement().satisfies(r -> {
      assertThat(r.ownerEmail()).isEqualTo("ada@example.com");
      assertThat(r.domain().hostname()).isEqualTo("go.acme.co");
    });
    assertThat(domains.adminPage(null, "verified", 10, null).items()).hasSize(1);
    assertThat(domains.adminPage(null, "pending", 10, null).items()).hasSize(1);
    domains.update(a.checked(true, null, NOW).block("abus"));
    assertThat(domains.adminPage(null, "blocked", 10, null).items()).hasSize(1);
    var first = domains.adminPage(null, null, 1, null);
    assertThat(first.nextCursor()).isNotNull();
    assertThat(domains.adminPage(null, null, 1, first.nextCursor()).items()).hasSize(1);
    assertThat(domains.adminFind(a.id())).isPresent();
  }

  @Test
  void cacheNotificationsUseTheScopedKeyAndTheHostname() throws Exception {
    List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
    var listener = new CacheInvalidationListener(url, user, password, seen::add, () -> {}).start();
    try {
      for (int i = 0; i < 50 && !listener.connected(); i++) Thread.sleep(100);
      assertThat(listener.connected()).isTrue();
      CustomDomain a = domain("go.acme.co");
      Link l = link("promo", a);
      links.update(l.disable("abus signalé", NOW));
      for (int i = 0; i < 50 && seen.stream().noneMatch(s -> s.equals("slug:" + a.id() + "/promo")); i++) Thread.sleep(100);
      assertThat(seen).contains("domain:go.acme.co", "slug:" + a.id() + "/promo", "link:" + l.id());
    } finally {
      listener.close();
    }
  }
}
