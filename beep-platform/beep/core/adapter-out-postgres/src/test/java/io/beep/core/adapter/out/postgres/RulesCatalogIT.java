package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.beep.core.domain.link.AppEntry;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.LinkRules;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
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

/** Règles de lien stockées en JSON, mot de passe, résolution avec règles, catalogue d'applications versionné. */
class RulesCatalogIT {
  static final Instant NOW = Instant.parse("2026-03-10T10:00:00Z");
  static DSLContext dsl;

  @BeforeAll
  static void start() {
    PGSimpleDataSource pg = new PGSimpleDataSource();
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || url.isBlank()) {
      PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine");
      c.start();
      url = c.getJdbcUrl();
      pg.setUser(c.getUsername());
      pg.setPassword(c.getPassword());
    }
    pg.setUrl(url);
    Flyway f = Flyway.configure().dataSource(pg).cleanDisabled(false).load();
    f.clean();
    f.migrate();
    dsl = DSL.using(pg, SQLDialect.POSTGRES);
  }

  Workspace ws;

  @BeforeEach
  void init() {
    dsl.execute("TRUNCATE users, slugs CASCADE");
    User u = User.register(UUID.randomUUID(), new Email("ada@example.com"), "h", "fr", NOW).verifyEmail(NOW);
    new PostgresUserAdapter(dsl).insert(u);
    ws = new PostgresWorkspaceAdapter(dsl).ensure(Workspace.personal(UUID.randomUUID(), u.id(), "ada", NOW));
  }

  @Test
  void rulesAndPasswordRoundTripAndReachTheResolver() {
    var links = new PostgresLinkAdapter(dsl);
    LinkRules rules = LinkRules.of(NOW.plusSeconds(86400), NOW, true, new LinkRules.Onelink("https://apps.apple.com/x", null, "https://x.example"),
        List.of(new LinkRules.Route(Set.of("CM"), Set.of(), Set.of("mobile"), Set.of("fr"), "https://x.example/cm")),
        List.of(new LinkRules.Variant("https://x.example/a", 1), new LinkRules.Variant("https://x.example/b", 2)), new LinkRules.Utm("news", "email", "spring", null, null));
    Link l = Link.create(UUID.randomUUID(), ws.id(), new Slug("rules"), Destination.parse("https://x.example"), null, NOW).withRules(rules, "hash123", NOW);
    links.insert(l);

    Link read = links.findById(l.id()).orElseThrow();
    assertThat(read.rules()).isEqualTo(rules);
    assertThat(read.passwordHash()).isEqualTo("hash123");

    var res = new PostgresSlugResolverAdapter(dsl, new PostgresBioPageAdapter(dsl)).resolve("rules");
    assertThat(res.rules()).isEqualTo(rules);
    assertThat(res.passwordHash()).isEqualTo("hash123");

    links.update(read.withRules(LinkRules.NONE, null, NOW));
    Link cleared = links.findById(l.id()).orElseThrow();
    assertThat(cleared.rules().isNone()).isTrue();
    assertThat(cleared.passwordProtected()).isFalse();
  }

  @Test
  void migrationSeedsThirtyAppsAndProRights() {
    var catalog = new PostgresAppCatalogAdapter(dsl);
    assertThat(catalog.all()).hasSize(30);
    assertThat(catalog.match("www.youtube.com")).get().extracting(AppEntry::code).isEqualTo("youtube");
    assertThat(catalog.match("m.instagram.com")).isPresent();
    assertThat(catalog.match("evil-instagram.com.attacker.example")).isEmpty();
    assertThat(catalog.match("notinstagram.com")).isEmpty();
    var pro = new PostgresPlanAdapter(dsl).findByCode("pro").orElseThrow();
    assertThat(pro.entitlements()).containsEntry("features.link_rules", 1L).containsEntry("features.link_routing", 0L).containsEntry("features.utm", 1L);
  }

  @Test
  void publishingBumpsTheVersionKeepsASnapshotAndRefreshesTheCache() {
    var catalog = new PostgresAppCatalogAdapter(dsl);
    assertThat(catalog.match("demo.example")).isEmpty();
    AppEntry v1 = catalog.publish(new AppEntry("demo", "Demo", List.of("demo.example"), "demo://{rest}", "com.demo", null, true, 0), "staff-1", NOW);
    assertThat(v1.version()).isEqualTo(1);
    assertThat(catalog.match("demo.example")).isPresent(); // publication = invalidation immédiate
    AppEntry v2 = catalog.publish(new AppEntry("demo", "Demo 2", List.of("demo.example"), "demo2://{rest}", "com.demo", null, true, 0), "staff-1", NOW);
    assertThat(v2.version()).isEqualTo(2);
    assertThat(dsl.fetchOne("SELECT count(*) FROM app_catalog_versions WHERE code = 'demo'").get(0, Long.class)).isEqualTo(2);
    catalog.setEnabled("demo", false, "staff-1", NOW);
    assertThat(catalog.match("demo.example")).isEmpty();
  }
}
