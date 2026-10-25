package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.analytics.Visit;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.link.LinkRulesCommand;
import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.app.redirect.DeeplinkSimulator;
import io.beep.core.app.redirect.LinkRouter;
import io.beep.core.app.redirect.Outcome;
import io.beep.core.app.redirect.ResolveSlugUseCase;
import io.beep.core.domain.link.AppEntry;
import io.beep.core.domain.link.DeeplinkDecision.Mode;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Règles de lien (expiration, mot de passe, ciblage, A/B, Onelink, UTM) et deeplinks (catalogue, décision par appareil). */
class RulesAndDeeplinksTest {
  static final String IPHONE = DeeplinkSimulator.PROFILES.get("iphone-safari");
  static final String ANDROID = DeeplinkSimulator.PROFILES.get("android-chrome");
  static final String IG_ANDROID = DeeplinkSimulator.PROFILES.get("android-instagram");
  static final String DESKTOP = DeeplinkSimulator.PROFILES.get("desktop-chrome");
  static final Map<String, Long> ELITE_RIGHTS = Map.of("links.stock_max", 50L, "features.link_rules", 1L, "features.link_routing", 1L, "features.utm", 1L);

  Platform p;
  User ada;
  double random;
  AppCatalogPort catalog;
  CreateLinkUseCase create;
  ResolveSlugUseCase resolve;

  @BeforeEach
  void setUp() {
    p = new Platform();
    p.plans.published.put("elite", new Plan("elite", "Elite", 1, 14999, true, ELITE_RIGHTS));
    catalog = new AppCatalogPort() {
      final AppEntry yt = new AppEntry("youtube", "YouTube", List.of("youtube.com"), "youtube://{host}{path}{query}", "com.google.android.youtube", "", true, 1);
      public Optional<AppEntry> match(String host) { return Optional.of(yt).filter(a -> a.matches(host)); }
      public List<AppEntry> all() { return List.of(yt); }
      public AppEntry publish(AppEntry e, String s, Instant a) { return e; }
      public void setEnabled(String c, boolean e, String s, Instant a) {}
    };
    LinkRouter router = new LinkRouter(p.recorder, ip -> "203.0.113.9".equals(ip) ? "CM" : "FR", catalog, () -> random);
    resolve = new ResolveSlugUseCase(p.resolver, p.recorder, router, p.hasher, p.limiter);
    create = new CreateLinkUseCase(p.workspaces, p.workspaceRepo, p.links, p.tx, p.site, p.clock).withHasher(p.hasher);
    ada = p.verifiedUser("ada@example.com");
    p.workspaceRepo.updatePlan(p.workspaces.ensureFor(ada).id(), "elite");
  }

  Visit visit(String ua, String ip, String lang, String query) {
    return new Visit(ip, ua, null, lang, query, true, p.now);
  }

  Link make(String slug, String dest, LinkRulesCommand rules) {
    return create.execute(ada, new CreateLinkUseCase.Command(dest, slug, null, null, rules));
  }

  LinkRulesCommand rules(String expires, String password, LinkRulesCommand.Onelink one, List<LinkRulesCommand.RouteCmd> routes, List<LinkRulesCommand.VariantCmd> variants,
      LinkRulesCommand.UtmCmd utm) {
    return new LinkRulesCommand(expires, password, null, one, routes, variants, utm);
  }

  @Test
  void expiredLinksStopRedirecting() {
    make("promo", "https://shop.example", rules(p.now.plus(Duration.ofDays(1)).toString(), null, null, null, null, null));
    assertThat(resolve.execute("promo", visit(DESKTOP, "1.1.1.1", "fr", null))).isInstanceOf(Outcome.Redirect.class);
    p.now = p.now.plus(Duration.ofDays(2));
    assertThat(resolve.execute("promo", visit(DESKTOP, "1.1.1.1", "fr", null))).isEqualTo(Outcome.EXPIRED);
    assertThatThrownBy(() -> make("past", "https://shop.example", rules(p.now.minusSeconds(5).toString(), null, null, null, null, null))).hasMessage("invalid_expiry");
  }

  @Test
  void passwordProtectedLinksNeedThePasswordAndAreRateLimited() {
    make("secret", "https://shop.example/vip", rules(null, "correct horse", null, null, null, null));
    Visit v = visit(DESKTOP, "1.1.1.1", "fr", null);
    assertThat(resolve.execute("secret", v)).isEqualTo(new Outcome.PasswordRequired(false));
    assertThat(p.sink.events).isEmpty();
    assertThat(resolve.unlock("secret", null, "wrong", v)).isEqualTo(new Outcome.PasswordRequired(true));
    assertThat(resolve.unlock("secret", null, "correct horse", v)).isEqualTo(new Outcome.Redirect("https://shop.example/vip"));
    assertThat(p.sink.events).hasSize(1);
    for (int i = 0; i < 12; i++) resolve.unlock("secret", null, "bad" + i, v);
    assertThat(resolve.unlock("secret", null, "correct horse", v)).isEqualTo(new Outcome.PasswordRequired(true));
  }

  @Test
  void routesTargetByCountryDeviceAndLanguageFirstMatchWins() {
    var cm = new LinkRulesCommand.RouteCmd(Set.of("cm"), Set.of(), Set.of(), Set.of(), "https://shop.example/cm");
    var fr = new LinkRulesCommand.RouteCmd(Set.of(), Set.of(), Set.of("desktop"), Set.of("fr"), "https://shop.example/fr-desktop");
    make("geo", "https://shop.example/default", rules(null, null, null, List.of(cm, fr), null, null));
    assertThat(resolve.execute("geo", visit(ANDROID, "203.0.113.9", "fr", null))).isEqualTo(new Outcome.Redirect("https://shop.example/cm"));
    assertThat(resolve.execute("geo", visit(DESKTOP, "1.1.1.1", "fr-FR", null))).isEqualTo(new Outcome.Redirect("https://shop.example/fr-desktop"));
    assertThat(resolve.execute("geo", visit(DESKTOP, "1.1.1.1", "en", null))).isEqualTo(new Outcome.Redirect("https://shop.example/default"));
  }

  @Test
  void abTestPicksVariantsByWeight() {
    var vs = List.of(new LinkRulesCommand.VariantCmd("https://shop.example/a", 1), new LinkRulesCommand.VariantCmd("https://shop.example/b", 3));
    make("abt", "https://shop.example/x", rules(null, null, null, null, vs, null));
    random = 0.1;
    assertThat(resolve.execute("abt", visit(DESKTOP, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://shop.example/a"));
    random = 0.5;
    assertThat(resolve.execute("abt", visit(DESKTOP, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://shop.example/b"));
    assertThatThrownBy(() -> make("abt2", "https://shop.example/x", rules(null, null, null, null, List.of(vs.get(0)), null))).hasMessage("invalid_rule");
  }

  @Test
  void onelinkSendsEachPlatformToItsStore() {
    var one = new LinkRulesCommand.Onelink("https://apps.apple.com/app/x", "https://play.google.com/store/apps/details?id=x", "https://x.example");
    make("get", "https://x.example", rules(null, null, one, null, null, null));
    assertThat(resolve.execute("get", visit(IPHONE, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://apps.apple.com/app/x"));
    assertThat(resolve.execute("get", visit(ANDROID, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://play.google.com/store/apps/details?id=x"));
    assertThat(resolve.execute("get", visit(DESKTOP, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://x.example"));
  }

  @Test
  void utmIsAddedWithoutOverwritingExistingParameters() {
    var utm = new LinkRulesCommand.UtmCmd("newsletter", "email", "spring sale", null, null);
    make("utm", "https://shop.example/p?utm_medium=keep", rules(null, null, null, null, null, utm));
    assertThat(resolve.execute("utm", visit(DESKTOP, "1.1.1.1", "fr", "ref=abc")))
        .isEqualTo(new Outcome.Redirect("https://shop.example/p?utm_medium=keep&ref=abc&utm_source=newsletter&utm_campaign=spring+sale"));
    assertThatThrownBy(() -> make("utm2", "https://shop.example", rules(null, null, null, null, null, new LinkRulesCommand.UtmCmd("a<b", null, null, null, null)))).hasMessage("invalid_utm");
  }

  @Test
  void plansGateTheRuleFeatures() {
    User eve = p.verifiedUser("eve@example.com");
    var exp = rules(p.now.plus(Duration.ofDays(3)).toString(), null, null, null, null, null);
    assertThatThrownBy(() -> create.execute(eve, new CreateLinkUseCase.Command("https://shop.example", "gate1", null, null, exp))).hasMessage("feature_unavailable");
    assertThatThrownBy(() -> create.execute(eve, new CreateLinkUseCase.Command("https://shop.example", "gate2", null, null, rules(null, "secret1", null, null, null, null))))
        .hasMessage("feature_unavailable");
    var route = new LinkRulesCommand.RouteCmd(Set.of("FR"), Set.of(), Set.of(), Set.of(), "https://x.example");
    assertThatThrownBy(() -> create.execute(eve, new CreateLinkUseCase.Command("https://shop.example", "gate3", null, null, rules(null, null, null, List.of(route), null, null))))
        .hasMessage("feature_unavailable");
    create.execute(eve, new CreateLinkUseCase.Command("https://shop.example", "open1", null, null,
        rules(null, null, new LinkRulesCommand.Onelink("https://apps.apple.com/x", null, null), null, null, null)));
  }

  @Test
  void androidFollowsTheIntentIosGetsAnInterstitial() {
    make("vid", "https://www.youtube.com/watch?v=abc", null);
    var android = (Outcome.Deeplink) resolve.execute("vid", visit(ANDROID, "1.1.1.1", "fr", null));
    assertThat(android.mode()).isEqualTo(Mode.ANDROID_INTENT);
    assertThat(android.plan().androidUrl()).startsWith("intent://www.youtube.com/watch?v=abc#Intent;scheme=https;package=com.google.android.youtube;");
    var ios = (Outcome.Deeplink) resolve.execute("vid", visit(IPHONE, "1.1.1.1", "fr", null));
    assertThat(ios.mode()).isEqualTo(Mode.INTERSTITIAL);
    assertThat(ios.plan().iosUrl()).isEqualTo("youtube://www.youtube.com/watch?v=abc");
    assertThat(resolve.execute("vid", visit(DESKTOP, "1.1.1.1", "fr", null))).isEqualTo(new Outcome.Redirect("https://www.youtube.com/watch?v=abc"));
  }

  @Test
  void inAppBrowsersGetTheInterstitialEvenOnAndroid() {
    make("vid", "https://www.youtube.com/watch?v=abc", null);
    var d = (Outcome.Deeplink) resolve.execute("vid", visit(IG_ANDROID, "1.1.1.1", "fr", null));
    assertThat(d.mode()).isEqualTo(Mode.INTERSTITIAL);
    assertThat(d.inApp()).isTrue();
  }

  @Test
  void deeplinkCanBeDisabledPerLinkAndBotsGetTheWebRedirect() {
    make("off", "https://www.youtube.com/watch?v=abc", new LinkRulesCommand(null, null, false, null, null, null, null));
    assertThat(resolve.execute("off", visit(ANDROID, "1.1.1.1", "fr", null))).isInstanceOf(Outcome.Redirect.class);
    make("bot", "https://www.youtube.com/watch?v=abc", null);
    assertThat(resolve.execute("bot", visit("WhatsApp/2.23 A", "1.1.1.1", "fr", null))).isInstanceOf(Outcome.Redirect.class);
  }

  @Test
  void simulatorExplainsWhatEachDeviceSees() {
    var sim = new DeeplinkSimulator(catalog);
    assertThat(sim.simulate("https://youtube.com/@beep", "android-chrome").mode()).isEqualTo("android_intent");
    assertThat(sim.simulate("https://youtube.com/@beep", "iphone-instagram").inApp()).isTrue();
    assertThat(sim.simulate("https://unknown.example/x", "iphone-safari").appFound()).isFalse();
    assertThatThrownBy(() -> sim.simulate("https://youtube.com", "nope")).hasMessage("invalid_request");
  }

  @Test
  void catalogEntriesAreValidatedBeforePublishing() {
    var uc = new io.beep.core.app.admin.AdminCatalogUseCase(catalog, new DeeplinkSimulator(catalog), p.tx, p.clock);
    uc.publish(new AppEntry("demo", "Demo", List.of("demo.example"), "demo://{rest}", "com.demo", "", true, 0), "staff");
    for (AppEntry bad : new AppEntry[] {
        new AppEntry("demo", "Demo", List.of("demo.example"), "javascript:alert(1)", null, "", true, 0),
        new AppEntry("Demo!", "Demo", List.of("demo.example"), "demo://x", null, "", true, 0),
        new AppEntry("demo", "Demo", List.of("not a host"), "demo://x", null, "", true, 0),
        new AppEntry("demo", "Demo", List.of("demo.example"), null, null, "", true, 0)}) {
      assertThatThrownBy(() -> uc.publish(bad, "staff")).hasMessage("invalid_app");
    }
  }
}
