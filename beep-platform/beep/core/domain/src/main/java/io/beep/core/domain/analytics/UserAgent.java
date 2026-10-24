package io.beep.core.domain.analytics;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lecture d'un User-Agent : type d'appareil, système, navigateur et détection des robots
 * (liste d'agents connus + heuristiques). Sans base externe : rapide et suffisant pour des agrégats.
 * Les aperçus de liens (WhatsApp, réseaux sociaux) sont des robots : ils ne comptent pas comme des clics humains.
 */
public record UserAgent(DeviceType device, String os, String browser, boolean bot) {
  private static final Pattern BOT = Pattern.compile(
      "bot|crawl|spider|slurp|scrap|preview|facebookexternalhit|facebookcatalog|whatsapp|telegram|skypeuripreview|embedly|"
          + "curl/|wget|python|httpclient|okhttp|go-http-client|java/|libwww|axios|node-fetch|undici|postman|insomnia|"
          + "headless|phantom|lighthouse|pagespeed|pingdom|uptime|monitor|statuscake|k6/|apache-httpclient|feedfetcher|"
          + "mediapartners|google-inspectiontool|bingpreview|yandex|baidu|ahrefs|semrush|mj12|dotbot|petalbot|bytespider|gptbot|claudebot|ccbot",
      Pattern.CASE_INSENSITIVE);

  public static UserAgent parse(String raw) {
    if (raw == null || raw.isBlank() || raw.length() < 8) return new UserAgent(DeviceType.BOT, "other", "other", true);
    String ua = raw.length() > 512 ? raw.substring(0, 512) : raw;
    if (BOT.matcher(ua).find()) return new UserAgent(DeviceType.BOT, os(ua), "bot", true);
    return new UserAgent(device(ua), os(ua), browser(ua), false);
  }

  private static DeviceType device(String ua) {
    String l = ua.toLowerCase(Locale.ROOT);
    if (l.contains("ipad") || l.contains("tablet") || (l.contains("android") && !l.contains("mobile"))) return DeviceType.TABLET;
    if (l.contains("mobi") || l.contains("iphone") || l.contains("ipod") || l.contains("android")) return DeviceType.MOBILE;
    if (l.contains("windows") || l.contains("macintosh") || l.contains("x11") || l.contains("cros") || l.contains("linux")) return DeviceType.DESKTOP;
    return DeviceType.OTHER;
  }

  private static String os(String ua) {
    String l = ua.toLowerCase(Locale.ROOT);
    if (l.contains("iphone") || l.contains("ipad") || l.contains("ipod")) return "ios";
    if (l.contains("android")) return "android";
    if (l.contains("windows")) return "windows";
    if (l.contains("cros")) return "chromeos";
    if (l.contains("mac os x") || l.contains("macintosh")) return "macos";
    if (l.contains("linux") || l.contains("x11")) return "linux";
    return "other";
  }

  private static String browser(String ua) {
    // Navigateurs intégrés aux applications d'abord : ils imitent Safari ou Chrome.
    if (ua.contains("Instagram")) return "instagram";
    if (ua.contains("FBAN") || ua.contains("FBAV") || ua.contains("FB_IAB")) return "facebook";
    if (ua.contains("musical_ly") || ua.contains("TikTok") || ua.contains("BytedanceWebview")) return "tiktok";
    if (ua.contains("LinkedInApp")) return "linkedin";
    if (ua.contains("Snapchat")) return "snapchat";
    if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "edge";
    if (ua.contains("OPR/") || ua.contains("Opera")) return "opera";
    if (ua.contains("SamsungBrowser")) return "samsung";
    if (ua.contains("UCBrowser")) return "uc";
    if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "firefox";
    if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "chrome";
    if (ua.contains("Safari/") && ua.contains("Version/")) return "safari";
    return "other";
  }
}
