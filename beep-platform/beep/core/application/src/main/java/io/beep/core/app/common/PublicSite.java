package io.beep.core.app.common;

import java.net.URI;
import java.util.Locale;

/** Adresse publique du site (`BEEP_PUBLIC_URL`) : base des liens courts, des QR dynamiques et des retours OAuth. */
public record PublicSite(String url) {
  public PublicSite {
    url = url.replaceAll("/+$", "");
  }

  public String host() {
    String h = URI.create(url).getHost();
    return h == null ? "" : h.toLowerCase(Locale.ROOT);
  }

  public String shortUrl(String slug) {
    return url + "/" + slug;
  }

  /** URL courte d'un lien : sur son domaine personnalisé (https) s'il en a un, sinon sur le domaine de Beep. */
  public String shortUrl(io.beep.core.domain.link.Link link) {
    return link.domainHost() == null ? shortUrl(link.slug().value()) : "https://" + link.domainHost() + "/" + link.slug().value();
  }
}
