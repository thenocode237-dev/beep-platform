package io.beep.core.domain.link;

import io.beep.core.domain.analytics.DeviceType;
import io.beep.core.domain.analytics.UserAgent;
import java.util.Set;

/**
 * Que faire d'un visiteur dont la destination a une application ? Sur Android hors navigateur intégré, l'intent est suivi directement (302) ;
 * sur iOS, et dans les navigateurs intégrés des réseaux sociaux (Instagram, Facebook, TikTok…) qui bloquent l'ouverture d'applications,
 * une page intermédiaire tente l'ouverture et propose de sortir du navigateur intégré. Ordinateurs, tablettes de bureau et robots : redirection web normale.
 */
public final class DeeplinkDecision {
  private DeeplinkDecision() {}

  public enum Mode {
    /** Redirection web classique. */
    WEB,
    /** 302 vers l'intent Android (repli web intégré à l'intent). */
    ANDROID_INTENT,
    /** Page intermédiaire : ouverture de l'application, repli web, aide à la sortie du navigateur intégré. */
    INTERSTITIAL
  }

  private static final Set<String> IN_APP = Set.of("instagram", "facebook", "tiktok", "linkedin", "snapchat");

  public static boolean inAppBrowser(UserAgent ua) {
    return IN_APP.contains(ua.browser());
  }

  public static Mode decide(UserAgent ua, AppEntry.Plan plan) {
    if (ua.bot() || (ua.device() != DeviceType.MOBILE && ua.device() != DeviceType.TABLET)) return Mode.WEB;
    boolean ios = "ios".equals(ua.os());
    boolean android = "android".equals(ua.os());
    if (!ios && !android) return Mode.WEB;
    if (ios && plan.iosUrl() == null && !inAppBrowser(ua)) return Mode.WEB; // liens universels : iOS ouvre l'application depuis l'adresse web
    if (android && plan.androidUrl() == null) return Mode.WEB;
    if (inAppBrowser(ua)) return Mode.INTERSTITIAL;
    return android ? Mode.ANDROID_INTENT : Mode.INTERSTITIAL;
  }
}
