package io.beep.core.domain.link;

import io.beep.core.domain.DomainException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Règles de redirection d'un lien (phase 2). Ordre d'application : expiration → ciblage (routes géo / appareil / langue, premier qui correspond) →
 * test A/B (tirage pondéré) → Onelink (destination par système) → destination par défaut du lien. Les UTM sont ajoutés à la destination choisie,
 * sans jamais écraser un paramètre déjà présent (dans la destination ou dans l'URL partagée).
 *
 * @param deeplink ouvrir l'application mobile quand la destination en a une dans le catalogue (vrai par défaut)
 */
public record LinkRules(Instant expiresAt, boolean deeplink, Onelink onelink, List<Route> routes, List<Variant> variants, Utm utm) {
  public static final int MAX_ROUTES = 10;
  public static final int MAX_VARIANTS = 5;
  public static final LinkRules NONE = new LinkRules(null, true, null, List.of(), List.of(), null);

  private static final Set<String> OS = Set.of("ios", "android", "windows", "macos", "linux", "chromeos");
  private static final Set<String> DEVICES = Set.of("mobile", "tablet", "desktop");
  private static final Pattern PARAM = Pattern.compile("[\\p{L}\\p{N} _.:+/@%-]{1,100}");

  public LinkRules {
    routes = List.copyOf(routes);
    variants = List.copyOf(variants);
  }

  /** iOS / Android / ordinateur : un seul lien pour tous les magasins et plateformes. */
  public record Onelink(String ios, String android, String desktop) {
    boolean empty() {
      return ios == null && android == null && desktop == null;
    }
  }

  /** Une règle de ciblage : tous les critères renseignés doivent correspondre. */
  public record Route(Set<String> countries, Set<String> os, Set<String> devices, Set<String> languages, String url) {
    public Route {
      countries = Set.copyOf(countries);
      os = Set.copyOf(os);
      devices = Set.copyOf(devices);
      languages = Set.copyOf(languages);
    }

    boolean matches(Visitor v) {
      return (countries.isEmpty() || (v.country() != null && countries.contains(v.country()))) && (os.isEmpty() || os.contains(v.os()))
          && (devices.isEmpty() || devices.contains(v.device())) && (languages.isEmpty() || (v.language() != null && languages.contains(v.language())));
    }
  }

  public record Variant(String url, int weight) {}

  public record Utm(String source, String medium, String campaign, String term, String content) {
    Map<String, String> params() {
      Map<String, String> m = new LinkedHashMap<>();
      if (source != null) m.put("utm_source", source);
      if (medium != null) m.put("utm_medium", medium);
      if (campaign != null) m.put("utm_campaign", campaign);
      if (term != null) m.put("utm_term", term);
      if (content != null) m.put("utm_content", content);
      return m;
    }
  }

  /** Signaux du visiteur (déjà normalisés) servant au ciblage. */
  public record Visitor(String country, String os, String device, String language) {}

  public boolean isNone() {
    return equals(NONE);
  }

  public boolean expired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  /** Fonctions réservées à certains plans (specs §6.1) : expiration et UTM = Pro ; ciblage et A/B = Elite. Onelink et deeplinks = tous. */
  public boolean usesRulesFeature() {
    return expiresAt != null;
  }

  public boolean usesRoutingFeature() {
    return !routes.isEmpty() || !variants.isEmpty();
  }

  public boolean usesUtmFeature() {
    return utm != null;
  }

  /**
   * Destination choisie pour ce visiteur, ou null pour la destination par défaut du lien.
   * `random` ∈ [0, 1[ : tirage du test A/B (injecté pour des tests déterministes).
   */
  public String select(Visitor v, double random) {
    for (Route r : routes) if (r.matches(v)) return r.url();
    if (!variants.isEmpty()) {
      int total = variants.stream().mapToInt(Variant::weight).sum();
      double point = random * total;
      int acc = 0;
      for (Variant variant : variants) {
        acc += variant.weight();
        if (point < acc) return variant.url();
      }
      return variants.get(variants.size() - 1).url();
    }
    if (onelink != null) {
      String url = switch (v.os()) {
        case "ios" -> onelink.ios();
        case "android" -> onelink.android();
        default -> onelink.desktop();
      };
      if (url == null && "desktop".equals(v.device())) url = onelink.desktop();
      if (url != null) return url;
    }
    return null;
  }

  /** Requête à ajouter à la destination : paramètres de l'URL partagée d'abord, puis UTM absents de la destination et de la requête. */
  public String query(String destinationUrl, String incoming) {
    StringBuilder q = new StringBuilder(incoming == null ? "" : incoming);
    if (utm != null) {
      String existing = (incoming == null ? "" : incoming) + "&" + destinationUrl;
      for (Map.Entry<String, String> e : utm.params().entrySet()) {
        if (existing.contains(e.getKey() + "=")) continue;
        if (q.length() > 0) q.append('&');
        q.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
      }
    }
    return q.length() == 0 ? null : q.toString();
  }

  // ---- Construction validée (saisie utilisateur) ----
  public static LinkRules of(Instant expiresAt, Instant now, Boolean deeplink, Onelink onelink, List<Route> routes, List<Variant> variants, Utm utm) {
    if (expiresAt != null && !expiresAt.isAfter(now)) throw new DomainException("invalid_expiry");
    if (expiresAt != null && expiresAt.isAfter(now.plusSeconds(10L * 366 * 86_400))) throw new DomainException("invalid_expiry");
    Onelink ol = onelink == null ? null : new Onelink(url(onelink.ios()), url(onelink.android()), url(onelink.desktop()));
    if (ol != null && ol.empty()) ol = null;
    List<Route> rs = new ArrayList<>();
    for (Route r : routes == null ? List.<Route>of() : routes) {
      Set<String> countries = upper(r.countries());
      Set<String> os = lower(r.os());
      Set<String> devices = lower(r.devices());
      Set<String> languages = lower(r.languages());
      if (!countries.stream().allMatch(c -> c.matches("[A-Z]{2}")) || !OS.containsAll(os) || !DEVICES.containsAll(devices) || !languages.stream().allMatch(l -> l.matches("[a-z]{2}"))) {
        throw new DomainException("invalid_rule");
      }
      if (countries.isEmpty() && os.isEmpty() && devices.isEmpty() && languages.isEmpty()) throw new DomainException("invalid_rule");
      rs.add(new Route(countries, os, devices, languages, url(r.url())));
    }
    List<Variant> vs = new ArrayList<>();
    for (Variant v : variants == null ? List.<Variant>of() : variants) {
      if (v.weight() < 1 || v.weight() > 100) throw new DomainException("invalid_rule");
      vs.add(new Variant(url(v.url()), v.weight()));
    }
    if (rs.size() > MAX_ROUTES || vs.size() > MAX_VARIANTS || vs.size() == 1) throw new DomainException("invalid_rule"); // un test A/B compare au moins deux variantes
    Utm u = utm == null ? null : new Utm(param(utm.source()), param(utm.medium()), param(utm.campaign()), param(utm.term()), param(utm.content()));
    if (u != null && u.params().isEmpty()) u = null;
    return new LinkRules(expiresAt, deeplink == null || deeplink, ol, rs, vs, u);
  }

  private static String url(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return Destination.parse(raw).url();
  }

  private static String param(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String v = raw.strip();
    if (!PARAM.matcher(v).matches()) throw new DomainException("invalid_utm");
    return v;
  }

  private static Set<String> upper(Set<String> in) {
    Set<String> out = new java.util.LinkedHashSet<>();
    for (String s : in) out.add(s.strip().toUpperCase(Locale.ROOT));
    return out;
  }

  private static Set<String> lower(Set<String> in) {
    Set<String> out = new java.util.LinkedHashSet<>();
    for (String s : in) out.add(s.strip().toLowerCase(Locale.ROOT));
    return out;
  }
}
