package io.beep.core.app.link;

import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.LinkRules;
import io.beep.core.domain.plan.EntitlementKeys;
import io.beep.core.domain.plan.Entitlements;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Règles saisies pour un lien. Présentes dans une modification = remplacent toutes les règles du lien ; le mot de passe est à part
 * (`password` null = inchangé, vide = supprimé). Les fonctions réservées à un plan sont refusées (`feature_unavailable`) uniquement quand
 * la commande les utilise : modifier autre chose sur un lien déjà réglé n'échoue jamais après une rétrogradation.
 */
public record LinkRulesCommand(String expiresAt, String password, Boolean deeplink, Onelink onelink, List<RouteCmd> routes, List<VariantCmd> variants, UtmCmd utm) {
  public record Onelink(String ios, String android, String desktop) {}

  public record RouteCmd(Set<String> countries, Set<String> os, Set<String> devices, Set<String> languages, String url) {}

  public record VariantCmd(String url, int weight) {}

  public record UtmCmd(String source, String medium, String campaign, String term, String content) {}

  public record Applied(LinkRules rules, String passwordHash) {}

  public static final int MAX_PASSWORD = 200;

  /** Valide, vérifie les droits du plan et retourne les règles et l'empreinte du mot de passe à enregistrer. */
  public Applied apply(Link current, Entitlements entitlements, PasswordHasherPort hasher, Instant now) {
    Instant expiry = null;
    if (expiresAt != null && !expiresAt.isBlank()) {
      try {
        expiry = Instant.parse(expiresAt.strip());
      } catch (DateTimeParseException e) {
        throw new DomainException("invalid_expiry");
      }
    }
    LinkRules rules = LinkRules.of(expiry, now, deeplink,
        onelink == null ? null : new LinkRules.Onelink(blank(onelink.ios()), blank(onelink.android()), blank(onelink.desktop())),
        routes == null ? List.of() : routes.stream().map(r -> new LinkRules.Route(nz(r.countries()), nz(r.os()), nz(r.devices()), nz(r.languages()), r.url())).toList(),
        variants == null ? List.of() : variants.stream().map(v -> new LinkRules.Variant(v.url(), v.weight())).toList(),
        utm == null ? null : new LinkRules.Utm(utm.source(), utm.medium(), utm.campaign(), utm.term(), utm.content()));

    String hash = current.passwordHash();
    boolean setsPassword = password != null && !password.isEmpty();
    if (password != null && password.isEmpty()) hash = null;
    if (setsPassword) {
      if (password.length() < 4 || password.length() > MAX_PASSWORD) throw new DomainException("invalid_password");
      hash = hasher.hash(password);
    }
    // droits : seulement pour ce que la commande active (ou modifie)
    LinkRules before = current.rules();
    if ((rules.usesRulesFeature() && !same(before.expiresAt(), rules.expiresAt())) || setsPassword) require(entitlements, EntitlementKeys.FEATURE_LINK_RULES);
    if (rules.usesRoutingFeature() && !(before.routes().equals(rules.routes()) && before.variants().equals(rules.variants()))) require(entitlements, EntitlementKeys.FEATURE_LINK_ROUTING);
    if (rules.usesUtmFeature() && !rules.utm().equals(before.utm())) require(entitlements, EntitlementKeys.FEATURE_UTM);
    return new Applied(rules, hash);
  }

  private static void require(Entitlements e, String feature) {
    if (!e.enabled(feature)) throw new DomainException("feature_unavailable");
  }

  private static boolean same(Instant a, Instant b) {
    return a == null ? b == null : a.equals(b);
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private static <T> Set<T> nz(Set<T> s) {
    return s == null ? Set.of() : s;
  }
}
