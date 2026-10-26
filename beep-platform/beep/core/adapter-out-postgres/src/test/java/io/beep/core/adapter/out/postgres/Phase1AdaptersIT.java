package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.SlugResolverPort.Resolution;
import io.beep.core.domain.analytics.ClickEvent;
import io.beep.core.domain.analytics.DeviceType;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.invitation.Invitation;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.qr.QrCode;
import io.beep.core.domain.qr.QrStyle;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
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

/** Adapters de la phase 1 sur un PostgreSQL réel (TEST_DATABASE_URL, sinon Testcontainers), migrations rejouées de zéro. */
class Phase1AdaptersIT {
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

  PostgresUserAdapter users;
  PostgresWorkspaceAdapter workspaces;
  PostgresLinkAdapter links;
  PostgresBioPageAdapter bios;
  PostgresQrCodeAdapter qrs;
  PostgresAnalyticsAdapter analytics;
  PostgresRollupAdapter rollups;
  Workspace ws;

  @BeforeEach
  void init() {
    dsl.execute("TRUNCATE users, slugs, click_events, rollups_hourly, rollups_daily, job_runs, beta_invitations, visitor_salts CASCADE");
    users = new PostgresUserAdapter(dsl);
    workspaces = new PostgresWorkspaceAdapter(dsl);
    links = new PostgresLinkAdapter(dsl);
    bios = new PostgresBioPageAdapter(dsl);
    qrs = new PostgresQrCodeAdapter(dsl);
    analytics = new PostgresAnalyticsAdapter(dsl);
    rollups = new PostgresRollupAdapter(dsl);
    ws = workspace("ada@example.com");
  }

  Workspace workspace(String email) {
    User u = User.register(UUID.randomUUID(), new Email(email), "h", "fr", Instant.parse("2026-01-01T00:00:00Z")).verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
    users.insert(u);
    return workspaces.ensure(Workspace.personal(UUID.randomUUID(), u.id(), "ada", Instant.parse("2026-01-01T00:00:00Z")));
  }

  Link link(String slug, String destination) {
    Link l = Link.create(UUID.randomUUID(), ws.id(), new Slug(slug), Destination.parse(destination), null, Instant.parse("2026-01-02T00:00:00Z"));
    links.insert(l);
    return l;
  }

  ClickEvent event(Instant at, Link l, EventKind kind, String visitor, String country, boolean bot) {
    return new ClickEvent(UUID.randomUUID(), at, ws.id(), kind, l.id(), null, null, null, visitor, country, DeviceType.MOBILE, "android", "chrome", "fr", "instagram.com", bot);
  }

  @Test
  void workspaceEnsureIsIdempotentAndPlansAreSeeded() {
    Workspace again = workspaces.ensure(Workspace.personal(UUID.randomUUID(), ws.ownerId(), "x", Instant.now()));
    assertThat(again.id()).isEqualTo(ws.id());
    var plans = new PostgresPlanAdapter(dsl);
    assertThat(plans.findAll()).extracting(p -> p.code()).containsExactly("starter", "pro", "elite");
    assertThat(plans.findByCode("starter").orElseThrow().entitlements()).containsEntry("links.stock_max", 5L).containsEntry("analytics.retention_days", 30L);
    plans.applyOverrides(ws.id(), Map.of("links.stock_max", 55L), "geste commercial", "staff-1", Instant.parse("2026-01-03T00:00:00Z"));
    plans.applyOverrides(ws.id(), java.util.Collections.singletonMap("links.stock_max", (Long) null), "retour au plan", "staff-1", Instant.now());
    assertThat(plans.overrides(ws.id())).isEmpty();
    plans.applyOverrides(ws.id(), Map.of("qr.stock_max", -1L), "illimité", "staff-2", Instant.parse("2026-01-03T00:00:00Z"));
    assertThat(plans.overrides(ws.id())).singleElement().satisfies(o -> assertThat(o.updatedBy()).isEqualTo("staff-2"));
  }

  @Test
  void linksAndBioPagesShareOneSlugNamespace() {
    Link l = link("promo", "https://shop.example");
    BioPage page = BioPage.create(UUID.randomUUID(), ws.id(), new Slug("promo"), "Ada", Instant.now());
    assertThatThrownBy(() -> bios.insert(page)).isInstanceOf(SlugRegistryPort.SlugTakenException.class);

    links.update(l.edit(new Slug("promo-2026"), l.destination(), "Promo", Instant.now()));
    assertThat(new PostgresSlugRegistryAdapter(dsl).isTaken(new Slug("promo"))).isFalse();
    bios.insert(page);
    assertThat(new PostgresSlugRegistryAdapter(dsl).isTaken(new Slug("promo"))).isTrue();
    links.delete(l.id());
    assertThat(new PostgresSlugRegistryAdapter(dsl).isTaken(new Slug("promo-2026"))).isFalse();
  }

  @Test
  void staffSearchFindsLinksBySlugDestinationOrOwnerEmailWithKeysetPages() {
    Link a = link("promo-a", "https://shop.example/a");
    link("promo-b", "https://other.example/b");
    var bySlug = links.adminSearch("promo-a", null, 10, null);
    assertThat(bySlug.items()).singleElement().satisfies(r -> {
      assertThat(r.link()).isEqualTo(a);
      assertThat(r.ownerEmail()).isEqualTo("ada@example.com");
    });
    assertThat(links.adminSearch("ADA@", null, 10, null).items()).hasSize(2);
    var first = links.adminSearch(null, ws.id(), 1, null);
    assertThat(first.nextCursor()).isNotNull();
    assertThat(links.adminSearch(null, ws.id(), 1, first.nextCursor()).items()).hasSize(1);
    assertThat(links.adminFind(a.id())).isPresent();
    assertThat(links.adminSearch("100%_", null, 10, null).items()).isEmpty(); // jokers neutralisés
  }

  @Test
  void bioPagesRoundTripWithOrderedBlocksAndSeo() {
    BioPage page = BioPage.create(UUID.randomUUID(), ws.id(), new Slug("naya"), "Atelier Naya", Instant.parse("2026-01-02T00:00:00Z"));
    bios.insert(page);
    List<BioBlock> blocks = List.of(BioBlock.of("b00002", BioBlock.Type.HEADING, Map.of("text", "Titre")),
        BioBlock.of("b00001", BioBlock.Type.LINK, Map.of("label", "Boutique", "url", "https://shop.example")));
    bios.replaceBlocks(page.id(), blocks, Instant.parse("2026-01-03T00:00:00Z"));
    BioPage read = bios.findById(page.id()).orElseThrow();
    assertThat(read.blocks()).extracting(BioBlock::id).containsExactly("b00002", "b00001");
    assertThat(read.blocks().get(1).config()).containsEntry("url", "https://shop.example");
    assertThat(bios.listByWorkspace(ws.id())).hasSize(1);
  }

  @Test
  void resolverSeesLinksBioPagesDisabledLinksAndSuspendedWorkspaces() {
    Link l = link("promo", "https://shop.example");
    var resolver = new PostgresSlugResolverAdapter(dsl, bios);
    assertThat(resolver.resolve("promo")).isEqualTo(new Resolution(Resolution.Kind.LINK, l.id(), ws.id(), "https://shop.example", false, false));
    assertThat(resolver.resolve("nope")).isEqualTo(Resolution.NONE);
    links.update(l.disable("hameçonnage", Instant.now()));
    assertThat(resolver.resolve("promo").disabled()).isTrue();
    workspaces.updateStatus(ws.id(), WorkspaceStatus.SUSPENDED);
    assertThat(resolver.resolve("promo").workspaceSuspended()).isTrue();

    QrCode qr = QrCode.dynamic(UUID.randomUUID(), ws.id(), "Ab3dEf9h", l.id(), QrStyle.DEFAULT, null, Instant.parse("2026-01-05T10:00:00Z"));
    qrs.insert(qr);
    assertThat(resolver.resolveQr("Ab3dEf9h")).get().satisfies(t -> assertThat(t.linkId()).isEqualTo(l.id()));
    assertThat(qrs.findById(qr.id()).orElseThrow()).isEqualTo(qr);
    assertThat(qrs.countByLink(l.id())).isEqualTo(1);
    assertThatThrownBy(() -> dsl.execute("DELETE FROM links WHERE id = ?", l.id())).hasMessageContaining("qr_codes"); // un lien suivi par un QR
  }

  @Test
  void eventsGoToMonthlyPartitionsCreatedOnDemandAndRollupsExcludeBots() {
    Link l = link("promo", "https://shop.example");
    Instant t = Instant.parse("2025-03-15T10:20:00Z"); // mois hors des partitions créées par la migration
    var store = new PostgresClickEventAdapter(dsl);
    store.insertBatch(List.of(event(t, l, EventKind.CLICK, "v1", "CM", false), event(t.plusSeconds(60), l, EventKind.CLICK, "v1", "CM", false),
        event(t.plusSeconds(120), l, EventKind.SCAN, "v2", null, false), event(t.plusSeconds(180), l, EventKind.CLICK, "bot", "US", true)));
    assertThat(dsl.fetchValue("SELECT count(*) FROM click_events_2025_03")).isEqualTo(4L);

    rollups.rollupHourly(Instant.parse("2025-03-15T00:00:00Z"), Instant.parse("2025-03-16T00:00:00Z"));
    rollups.rollupDaily(LocalDate.parse("2025-03-15"), LocalDate.parse("2025-03-15"));
    rollups.rollupDaily(LocalDate.parse("2025-03-15"), LocalDate.parse("2025-03-15")); // idempotent

    LocalDate d = LocalDate.parse("2025-03-15");
    assertThat(analytics.totals(Resource.LINK, l.id(), d, d)).isEqualTo(new io.beep.core.app.port.AnalyticsRepository.Totals(3, 2));
    assertThat(analytics.breakdown(Resource.LINK, l.id(), "country", d, d, 10)).extracting(b -> b.key() + "=" + b.clicks()).containsExactly("CM=2", "ZZ=1");
    assertThat(analytics.breakdown(Resource.LINK, l.id(), "source", d, d, 10)).extracting(b -> b.key() + "=" + b.clicks()).containsExactly("click=2", "scan=1");
    assertThat(analytics.daily(Resource.LINK, l.id(), d.minusDays(2), d)).extracting(p -> p.clicks()).containsExactly(0L, 0L, 3L);
    assertThat(analytics.hourly(Resource.LINK, l.id(), Instant.parse("2025-03-15T09:00:00Z"), Instant.parse("2025-03-15T12:00:00Z")))
        .extracting(p -> p.clicks()).containsExactly(0L, 3L, 0L);
    assertThat(analytics.totalsByResource(Resource.LINK, List.of(l.id(), UUID.randomUUID()), d, d)).containsExactly(Map.entry(l.id(), 3L));
    assertThat(analytics.workspaceDaily(ws.id(), d, d)).singleElement().satisfies(p -> assertThat(p.clicks()).isEqualTo(3));
  }

  @Test
  void retentionPurgesStarterDataBeyondThirtyDaysButNotOverriddenWorkspaces() {
    Link l = link("promo", "https://shop.example");
    Instant now = Instant.parse("2026-03-10T00:00:00Z");
    rollups.ensurePartitions(YearMonth.of(2026, 1), 3);
    var store = new PostgresClickEventAdapter(dsl);
    store.insertBatch(List.of(event(now.minus(Duration.ofDays(45)), l, EventKind.CLICK, "a", "CM", false), event(now.minus(Duration.ofDays(5)), l, EventKind.CLICK, "b", "CM", false)));
    rollups.rollupDaily(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-10"));
    assertThat(rollups.purgeExpiredEvents(now, 1)).isEqualTo(1);
    assertThat(rollups.purgeExpiredRollups(now)).isPositive();
    assertThat(dsl.fetchValue("SELECT count(*) FROM click_events")).isEqualTo(1L);

    new PostgresPlanAdapter(dsl).applyOverrides(ws.id(), Map.of("analytics.retention_days", -1L), "rétention illimitée", "s", now);
    store.insertBatch(List.of(event(now.minus(Duration.ofDays(60)), l, EventKind.CLICK, "c", "CM", false)));
    assertThat(rollups.purgeExpiredEvents(now, 100)).isZero();
  }

  @Test
  void oldPartitionsAreDroppedWholesale() {
    rollups.ensurePartitions(YearMonth.of(2024, 1), 1);
    List<String> dropped = rollups.dropPartitionsBefore(YearMonth.of(2024, 2));
    assertThat(dropped).contains("click_events_2024_01").doesNotContain("click_events_2024_02");
  }

  @Test
  void cacheIsInvalidatedOnCommitThroughListenNotify() throws Exception {
    Link l = link("promo", "https://old.example");
    CachingSlugResolverAdapter cache = new CachingSlugResolverAdapter(new PostgresSlugResolverAdapter(dsl, bios), 1000);
    try (CacheInvalidationListener listener = new CacheInvalidationListener(url, user, password, cache::invalidate, cache::invalidateAll).start()) {
      for (int i = 0; i < 50 && !listener.connected(); i++) Thread.sleep(100);
      assertThat(cache.resolve("promo").destination()).isEqualTo("https://old.example");
      dsl.execute("UPDATE links SET destination = 'https://new.example' WHERE id = ?", l.id()); // sans passer par le cache
      assertThat(cache.resolve("promo").destination()).isEqualTo("https://old.example"); // servi depuis le cache…
      String seen = null;
      for (int i = 0; i < 50; i++) {
        seen = cache.resolve("promo").destination();
        if (seen.equals("https://new.example")) break;
        Thread.sleep(100);
      }
      assertThat(seen).isEqualTo("https://new.example"); // …jusqu'à la notification
      assertThat(cache.resolve("unknown").kind()).isEqualTo(Resolution.Kind.NONE);
      link("unknown", "https://created.example"); // l'insertion dans `slugs` invalide l'entrée négative
      for (int i = 0; i < 50 && cache.resolve("unknown").kind() == Resolution.Kind.NONE; i++) Thread.sleep(100);
      assertThat(cache.resolve("unknown").kind()).isEqualTo(Resolution.Kind.LINK);
    }
  }

  @Test
  void invitationsAreRenewedUntilUsedAndConsumedOnce() {
    var invitations = new PostgresInvitationAdapter(dsl);
    Email email = new Email("eve@example.com");
    Instant now = Instant.parse("2026-03-10T00:00:00Z");
    invitations.upsert(Invitation.create(UUID.randomUUID(), email, "staff-1", now.minus(Duration.ofDays(40))));
    assertThat(invitations.consume(email, now)).isFalse(); // expirée
    invitations.upsert(Invitation.create(UUID.randomUUID(), email, "staff-2", now));
    assertThat(invitations.consume(email, now)).isTrue();
    assertThat(invitations.consume(email, now)).isFalse();
    Invitation used = invitations.upsert(Invitation.create(UUID.randomUUID(), email, "staff-3", now));
    assertThat(used.invitedBy()).isEqualTo("staff-2"); // une invitation utilisée n'est pas réécrite
    assertThat(invitations.page(10, null).items()).hasSize(1);
  }

  @Test
  void jobRunsSaltsAndPlatformMetrics() {
    var jobs = new PostgresJobRunAdapter(dsl);
    Instant t = Instant.parse("2026-03-10T10:00:00Z");
    long ok = jobs.start("analytics.rollup", t);
    jobs.finish(ok, t.plusSeconds(1), true, "horaires=3");
    long ko = jobs.start("analytics.rollup", t.plusSeconds(60));
    jobs.finish(ko, t.plusSeconds(61), false, "boom");
    var summary = jobs.summaries(t.minusSeconds(10)).get(0);
    assertThat(summary.lastOk()).isFalse();
    assertThat(summary.lastSuccessAt()).isEqualTo(t.plusSeconds(1));
    assertThat(summary.runs()).isEqualTo(2);
    assertThat(summary.failures()).isEqualTo(1);
    assertThat(jobs.lastSuccess("analytics.rollup")).contains(t.plusSeconds(1));

    var salts = new PostgresVisitorSaltAdapter(dsl);
    byte[] s1 = salts.saltFor(LocalDate.parse("2026-03-10"));
    assertThat(new PostgresVisitorSaltAdapter(dsl).saltFor(LocalDate.parse("2026-03-10"))).isEqualTo(s1); // partagé entre instances
    assertThat(salts.deleteBefore(LocalDate.parse("2026-03-11"))).isEqualTo(1);

    workspaces.markFirstLink(ws.id(), Instant.parse("2026-01-01T00:05:00Z"));
    var metrics = new PostgresPlatformMetricsAdapter(dsl);
    assertThat(metrics.userCounts().active()).isEqualTo(1);
    assertThat(metrics.activation(Instant.parse("2025-12-01T00:00:00Z"), Duration.ofMinutes(10))).isEqualTo(new io.beep.core.app.port.PlatformMetricsRepository.Activation(1, 1));
    assertThat(metrics.signupsPerDay(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"))).extracting(c -> c.count()).containsExactly(1L, 0L);
    assertThat(metrics.databaseBytes()).isPositive();
  }
}
