package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.link.UpdateLinkUseCase;
import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.app.redirect.Outcome;
import io.beep.core.domain.analytics.DeviceType;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinksAndRedirectTest {
  static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";

  Platform p;
  User ada;

  @BeforeEach
  void setUp() {
    p = new Platform();
    ada = p.verifiedUser("ada@example.com");
  }

  Visit visit(String query) {
    return new Visit("203.0.113.9", IPHONE, "https://www.instagram.com/p/1", "fr-CM,fr;q=0.9", query, true, p.now);
  }

  @Test
  void createsAPersonalWorkspaceOnFirstUseAndMeasuresActivation() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("shop.example/promo", null, "Promo"));
    assertThat(link.slug().value()).matches("[0-9A-Za-z]{7}");
    assertThat(link.destination().url()).isEqualTo("https://shop.example/promo");
    var ws = p.workspaceRepo.findByOwner(ada.id()).orElseThrow();
    assertThat(ws.planCode()).isEqualTo("starter");
    assertThat(ws.firstLinkAt()).isEqualTo(p.now);
    assertThat(p.workspaceRepo.locks).isEqualTo(1); // contrôle de quota sous verrou
  }

  @Test
  void customSlugsAreUniqueAcrossLinksAndBioPagesAndReservedOnesAreRefused() {
    p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", "promo", null));
    User bob = p.verifiedUser("bob@example.com");
    assertThatThrownBy(() -> p.createLink.execute(bob, new CreateLinkUseCase.Command("example.com", "promo", null))).hasMessage("slug_taken");
    assertThatThrownBy(() -> p.bio.create(bob, new io.beep.core.app.bio.BioPagesUseCase.CreateCommand("promo", "Bob"))).hasMessage("slug_taken");
    assertThatThrownBy(() -> p.createLink.execute(bob, new CreateLinkUseCase.Command("example.com", "Pricing", null))).hasMessage("reserved_slug");
    assertThatThrownBy(() -> p.createLink.execute(bob, new CreateLinkUseCase.Command("https://beep.example/abc", null, null))).hasMessage("destination_loop");
  }

  @Test
  void starterStockQuotaBlocksTheSixthLinkAndDeletingFreesAPlace() {
    Link first = null;
    for (int i = 0; i < 5; i++) {
      Link l = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/" + i, null, null));
      if (first == null) first = l;
    }
    assertThatThrownBy(() -> p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/6", null, null))).hasMessage("quota_exceeded");
    p.deleteLink.execute(ada, first.id());
    assertThat(p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/6", null, null))).isNotNull();
  }

  @Test
  void overridesFromTheStaffRaiseTheQuota() {
    for (int i = 0; i < 5; i++) p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/" + i, null, null));
    var ws = p.workspaceRepo.findByOwner(ada.id()).orElseThrow();
    var result = p.adminEntitlements.setOverrides(ws.id(), Map.of("links.stock_max", 55L), "offert à la bêta", "staff-1");
    assertThat(result.entitlements()).containsEntry("links.stock_max", 55L);
    assertThat(result.overrides()).singleElement().satisfies(o -> assertThat(o.updatedBy()).isEqualTo("staff-1"));
    assertThat(p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/6", null, null))).isNotNull();
    assertThatThrownBy(() -> p.adminEntitlements.setOverrides(ws.id(), Map.of("links.oops", 1L), "test", "s")).hasMessage("unknown_entitlement");
    assertThatThrownBy(() -> p.adminEntitlements.setOverrides(ws.id(), Map.of("links.stock_max", 1L), "", "s")).hasMessage("invalid_reason");
  }

  @Test
  void anotherWorkspacesLinkLooksNonexistent() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    User mallory = p.verifiedUser("mallory@example.com");
    assertThatThrownBy(() -> p.updateLink.execute(mallory, link.id(), new UpdateLinkUseCase.Command("evil.example", null, null))).hasMessage("not_found");
    assertThatThrownBy(() -> p.deleteLink.execute(mallory, link.id())).hasMessage("not_found");
    assertThatThrownBy(() -> p.stats.execute(mallory, new GetStatsUseCase.Query(Resource.LINK, link.id(), 30))).hasMessage("not_found");
  }

  @Test
  void redirectForwardsTheQueryAndRecordsAPseudonymousEvent() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("https://shop.example/p?id=1", "promo", null));
    Outcome out = p.resolveSlug.execute("promo", visit("utm_source=wa"));
    assertThat(out).isEqualTo(new Outcome.Redirect("https://shop.example/p?id=1&utm_source=wa"));
    var e = p.sink.events.get(0);
    assertThat(e.kind()).isEqualTo(EventKind.CLICK);
    assertThat(e.linkId()).isEqualTo(link.id());
    assertThat(e.country()).isEqualTo("CM");
    assertThat(e.device()).isEqualTo(DeviceType.MOBILE);
    assertThat(e.referrer()).isEqualTo("instagram.com");
    assertThat(e.lang()).isEqualTo("fr");
    assertThat(e.visitorHash()).hasSize(32).doesNotContain("203.0.113.9");
  }

  @Test
  void headRequestsAndPrefetchesAreNotCounted() {
    p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", "promo", null));
    Visit head = new Visit("203.0.113.9", IPHONE, null, null, null, false, p.now);
    assertThat(p.resolveSlug.execute("promo", head)).isInstanceOf(Outcome.Redirect.class);
    assertThat(p.sink.events).isEmpty();
  }

  @Test
  void unknownOrMalformedSlugsAreNotFoundWithoutLookup() {
    assertThat(p.resolveSlug.execute("nope", visit(null))).isEqualTo(Outcome.NOT_FOUND);
    assertThat(p.resolveSlug.execute("../etc", visit(null))).isEqualTo(Outcome.NOT_FOUND);
  }

  @Test
  void disabledLinksAndSuspendedAccountsShowAWarningInsteadOfRedirecting() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", "promo", null));
    p.adminLinks.disable(link.id(), "hameçonnage signalé");
    assertThat(p.resolveSlug.execute("promo", visit(null))).isEqualTo(Outcome.UNAVAILABLE);
    p.adminLinks.enable(link.id());
    assertThat(p.resolveSlug.execute("promo", visit(null))).isInstanceOf(Outcome.Redirect.class);

    p.adminUsers.setStatus(ada.id(), "suspended", "fraude avérée");
    assertThat(p.resolveSlug.execute("promo", visit(null))).isEqualTo(Outcome.UNAVAILABLE);
    assertThat(p.workspaceRepo.findByOwner(ada.id()).orElseThrow().status()).isEqualTo(WorkspaceStatus.SUSPENDED);
    assertThatThrownBy(() -> p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null))).hasMessage("workspace_suspended");
  }

  @Test
  void statsWindowIsCappedByThePlanRetentionAndBasicDimensionsOnlyForStarter() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    var view = p.stats.execute(ada, new GetStatsUseCase.Query(Resource.LINK, link.id(), 365));
    assertThat(view.days()).isEqualTo(30);
    assertThat(view.series()).hasSize(30);
    assertThat(view.breakdowns()).containsOnlyKeys("country", "device", "source");
    assertThat(view.exportAllowed()).isFalse();
    assertThatThrownBy(() -> p.stats.export(ada, new GetStatsUseCase.Query(Resource.LINK, link.id(), 30))).hasMessage("feature_unavailable");
    var today = p.stats.execute(ada, new GetStatsUseCase.Query(Resource.LINK, link.id(), 1));
    assertThat(today.granularity()).isEqualTo("hour");
    assertThat(today.series()).hasSize(24);
  }

  @Test
  void listShowsClicksOfTheLastThirtyDays() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    p.analytics.totals.put(link.id(), 42L);
    assertThat(p.listLinks.execute(ada, null, 20, null).items()).singleElement().satisfies(i -> assertThat(i.clicks30d()).isEqualTo(42));
  }
}
