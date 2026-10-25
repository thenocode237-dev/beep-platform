package io.beep.core.domain.link;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Une application du catalogue : les domaines qu'elle revendique et comment l'ouvrir sur iOS (schéma personnalisé) et Android (intent).
 *
 * <p>Modèles (`iosTemplate`, `androidTemplate`) : `{url}` adresse complète encodée, `{host}`, `{path}` (avec « / » initial), `{rest}` (chemin sans « / »),
 * `{path1}` premier segment du chemin, `{path2}` deuxième segment, `{query}` (avec « ? » si présent). Un modèle vide = pas de schéma : iOS ouvre alors
 * l'adresse web (liens universels). Par défaut Android utilise l'intent standard `intent://…;scheme=https;package=…`.
 */
public record AppEntry(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, int version) {
  public AppEntry {
    hosts = List.copyOf(hosts);
  }

  public boolean matches(String host) {
    String h = host.toLowerCase(Locale.ROOT);
    for (String candidate : hosts) if (h.equals(candidate) || h.endsWith("." + candidate)) return true;
    return false;
  }

  /** Plan d'ouverture pour une adresse web de cette application. */
  public Plan plan(String webUrl) {
    URI uri = URI.create(webUrl);
    String host = uri.getHost() == null ? "" : uri.getHost();
    String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
    String[] segments = path.replaceAll("^/+", "").split("/", -1);
    String ios = iosTemplate == null || iosTemplate.isBlank() ? null : expand(iosTemplate, webUrl, host, path, query, segments);
    String android;
    if (androidTemplate != null && !androidTemplate.isBlank()) {
      android = expand(androidTemplate, webUrl, host, path, query, segments);
    } else if (androidPackage != null && !androidPackage.isBlank()) {
      android = "intent://" + host + path + query + "#Intent;scheme=" + (uri.getScheme() == null ? "https" : uri.getScheme()) + ";package=" + androidPackage + ";S.browser_fallback_url="
          + URLEncoder.encode(webUrl, StandardCharsets.UTF_8) + ";end";
    } else {
      android = null;
    }
    return new Plan(name, webUrl, ios, android);
  }

  private static String expand(String template, String url, String host, String path, String query, String[] segments) {
    return template.replace("{url}", URLEncoder.encode(url, StandardCharsets.UTF_8)).replace("{host}", host).replace("{path}", path)
        .replace("{rest}", path.replaceAll("^/+", "")).replace("{path1}", segment(segments, 0)).replace("{path2}", segment(segments, 1)).replace("{query}", query);
  }

  private static String segment(String[] segments, int i) {
    return i < segments.length ? segments[i] : "";
  }

  /** Comment ouvrir l'application : adresse web de repli, schéma iOS, intent Android (null si non prévu). */
  public record Plan(String appName, String webUrl, String iosUrl, String androidUrl) {}
}
