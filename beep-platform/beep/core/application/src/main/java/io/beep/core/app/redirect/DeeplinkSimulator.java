package io.beep.core.app.redirect;

import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.analytics.UserAgent;
import io.beep.core.domain.link.AppEntry;
import io.beep.core.domain.link.DeeplinkDecision;
import io.beep.core.domain.link.Destination;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simulateur de comportement : pour une adresse et un profil d'appareil, indique si une application est trouvée dans le catalogue et ce que verrait
 * le visiteur (redirection web, intent Android, page intermédiaire). Utilisé dans l'espace client et par le staff pour tester une entrée du catalogue.
 */
public class DeeplinkSimulator {
  /** Profils d'appareils proposés (User-Agent représentatifs). */
  public static final Map<String, String> PROFILES = profiles();

  public record Result(String profile, boolean appFound, String appName, String mode, String webUrl, String iosUrl, String androidUrl, String os, boolean inApp, String note) {}

  private static Map<String, String> profiles() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("iphone-safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
    m.put("iphone-instagram", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 330.0.0.0.0");
    m.put("iphone-facebook", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/450.0.0.0.0]");
    m.put("android-chrome", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
    m.put("android-instagram", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Instagram 330.0.0.0.0 Android");
    m.put("android-tiktok", "Mozilla/5.0 (Linux; Android 13; TECNO KG5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 musical_ly_2023");
    m.put("desktop-chrome", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
    return m;
  }

  private final AppCatalogPort catalog;

  public DeeplinkSimulator(AppCatalogPort catalog) {
    this.catalog = catalog;
  }

  public Result simulate(String rawUrl, String profile) {
    String ua = PROFILES.get(profile);
    if (ua == null) throw new DomainException("invalid_request");
    Destination destination = Destination.parse(rawUrl);
    return simulate(destination, profile, UserAgent.parse(ua), catalog.match(destination.host()));
  }

  /** Simule une entrée précise (test d'une entrée avant sa publication). */
  public Result simulate(String rawUrl, String profile, AppEntry candidate) {
    String ua = PROFILES.get(profile);
    if (ua == null) throw new DomainException("invalid_request");
    Destination destination = Destination.parse(rawUrl);
    return simulate(destination, profile, UserAgent.parse(ua), Optional.of(candidate).filter(c -> c.matches(destination.host())));
  }

  private Result simulate(Destination destination, String profile, UserAgent ua, Optional<AppEntry> app) {
    if (app.isEmpty()) return new Result(profile, false, null, "web", destination.url(), null, null, ua.os(), DeeplinkDecision.inAppBrowser(ua), "Aucune application dans le catalogue pour ce domaine.");
    AppEntry.Plan plan = app.get().plan(destination.url());
    DeeplinkDecision.Mode mode = DeeplinkDecision.decide(ua, plan);
    String note = switch (mode) {
      case WEB -> "Redirection web : cet appareil n'ouvre pas d'application (ordinateur, robot, ou liens universels iOS).";
      case ANDROID_INTENT -> "Redirection 302 vers l'intent Android ; l'adresse web sert de repli si l'application est absente.";
      case INTERSTITIAL -> DeeplinkDecision.inAppBrowser(ua)
          ? "Navigateur intégré d'une application : page intermédiaire qui tente l'ouverture et aide à sortir du navigateur intégré."
          : "Page intermédiaire : tente le schéma iOS puis bascule sur l'adresse web.";
    };
    return new Result(profile, true, plan.appName(), mode.name().toLowerCase(java.util.Locale.ROOT), plan.webUrl(), plan.iosUrl(), plan.androidUrl(), ua.os(), DeeplinkDecision.inAppBrowser(ua), note);
  }
}
