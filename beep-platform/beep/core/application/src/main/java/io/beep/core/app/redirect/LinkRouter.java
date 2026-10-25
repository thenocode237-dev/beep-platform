package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.app.port.GeoIpPort;
import io.beep.core.domain.analytics.UserAgent;
import io.beep.core.domain.analytics.VisitSignals;
import io.beep.core.domain.link.AppEntry;
import io.beep.core.domain.link.DeeplinkDecision;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.LinkRules;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Décide où envoyer un visiteur d'un lien (ou d'un QR dynamique) : expiration, mot de passe, destination selon ses signaux
 * (ciblage, A/B, Onelink), UTM, puis ouverture d'application. Sans appel réseau : tout vient de la résolution mise en cache,
 * du catalogue en mémoire et de la base GeoIP locale. Les quotas n'interviennent jamais ici.
 */
public class LinkRouter {
  /** Lien résolu tel que le cache le fournit. */
  public record Resolved(UUID linkId, String destination, LinkRules rules, String passwordHash) {}

  private final ClickRecorder recorder;
  private final GeoIpPort geoIp;
  private final AppCatalogPort catalog;
  private final DoubleSupplier random;

  public LinkRouter(ClickRecorder recorder, GeoIpPort geoIp, AppCatalogPort catalog, DoubleSupplier random) {
    this.recorder = recorder;
    this.geoIp = geoIp;
    this.catalog = catalog;
    this.random = random;
  }

  /** Sans règles ni catalogue (tests, ou fonction désactivée) : redirection simple. */
  public static LinkRouter plain(ClickRecorder recorder) {
    return new LinkRouter(recorder, ip -> null, new AppCatalogPort() {
      public Optional<AppEntry> match(String host) { return Optional.empty(); }
      public java.util.List<AppEntry> all() { return java.util.List.of(); }
      public AppEntry publish(AppEntry entry, String staffId, java.time.Instant at) { throw new UnsupportedOperationException(); }
      public void setEnabled(String code, boolean enabled, String staffId, java.time.Instant at) { throw new UnsupportedOperationException(); }
    }, () -> 0.0);
  }

  public Outcome route(Resolved r, Visit visit, ClickRecorder.Target target, boolean passwordChecked) {
    LinkRules rules = r.rules() == null ? LinkRules.NONE : r.rules();
    if (rules.expired(visit.at())) return Outcome.EXPIRED;
    if (r.passwordHash() != null && !passwordChecked) return new Outcome.PasswordRequired(false);

    UserAgent ua = UserAgent.parse(visit.userAgent());
    String chosen = r.destination();
    if (!rules.isNone()) {
      LinkRules.Visitor v = new LinkRules.Visitor(ua.bot() ? null : upper(geoIp.country(visit.ip())), ua.os(), ua.device().name().toLowerCase(Locale.ROOT),
          VisitSignals.primaryLanguage(visit.acceptLanguage()));
      String selected = rules.select(v, random.getAsDouble());
      if (selected != null) chosen = selected;
    }
    recorder.record(visit, target);
    String url = new Destination(chosen).withQuery(rules.query(chosen, visit.query()));

    if (rules.deeplink() && !ua.bot()) {
      Optional<AppEntry> app = catalog.match(new Destination(chosen).host());
      if (app.isPresent()) {
        AppEntry.Plan plan = app.get().plan(url);
        DeeplinkDecision.Mode mode = DeeplinkDecision.decide(ua, plan);
        if (mode != DeeplinkDecision.Mode.WEB) return new Outcome.Deeplink(mode, plan, ua.os(), DeeplinkDecision.inAppBrowser(ua), ua.browser());
      }
    }
    return new Outcome.Redirect(url);
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }
}
