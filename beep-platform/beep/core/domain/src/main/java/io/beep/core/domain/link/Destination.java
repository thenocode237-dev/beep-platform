package io.beep.core.domain.link;

import io.beep.core.domain.DomainException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * URL de destination d'une redirection : http(s) uniquement, hôte obligatoire, sans identifiants intégrés
 * (`https://banque.com@pirate.example` est refusé), 2 048 caractères au plus.
 * Saisie « en un champ » : sans schéma, https:// est ajouté.
 */
public record Destination(String url) {
  public static final int MAX_LENGTH = 2048;

  public Destination {
    if (url == null) throw new DomainException("invalid_destination");
  }

  public static Destination parse(String raw) {
    if (raw == null) throw new DomainException("invalid_destination");
    String v = raw.strip();
    if (v.isEmpty() || v.length() > MAX_LENGTH || v.chars().anyMatch(c -> c < 0x21 || c == 0x7f)) throw new DomainException("invalid_destination");
    if (!v.contains("://")) v = "https://" + v;
    URI uri;
    try {
      uri = new URI(v);
    } catch (URISyntaxException e) {
      throw new DomainException("invalid_destination");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) throw new DomainException("invalid_destination");
    if (uri.getRawUserInfo() != null) throw new DomainException("invalid_destination");
    String host = uri.getHost();
    if (host == null || host.isBlank() || !host.contains(".") && !host.equals("localhost")) throw new DomainException("invalid_destination");
    return new Destination(v);
  }

  /** Hôte en minuscules (forme ASCII pour les noms internationalisés). */
  public String host() {
    try {
      String h = new URI(url).getHost();
      return h == null ? "" : IDN.toASCII(h).toLowerCase(Locale.ROOT);
    } catch (URISyntaxException | IllegalArgumentException e) {
      return "";
    }
  }

  /** Une destination qui pointe vers Beepclick lui-même créerait des chaînes ou des boucles de redirection. */
  public void requireNotHost(String ownHost) {
    if (ownHost != null && !ownHost.isBlank() && host().equals(ownHost.toLowerCase(Locale.ROOT))) throw new DomainException("destination_loop");
  }

  /**
   * Ajoute les paramètres de la requête entrante (ex. UTM ajoutés par celui qui partage) à la destination,
   * avant l'éventuel fragment.
   */
  public String withQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) return url;
    int hash = url.indexOf('#');
    String base = hash < 0 ? url : url.substring(0, hash);
    String fragment = hash < 0 ? "" : url.substring(hash);
    return base + (base.contains("?") ? "&" : "?") + rawQuery + fragment;
  }

  @Override
  public String toString() {
    return url;
  }
}
