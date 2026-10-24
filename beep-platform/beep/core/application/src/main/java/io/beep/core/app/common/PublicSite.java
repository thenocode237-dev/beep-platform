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
}
