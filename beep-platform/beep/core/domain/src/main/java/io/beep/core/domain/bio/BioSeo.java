package io.beep.core.domain.bio;

import io.beep.core.domain.DomainException;

/** Référencement d'une page bio : titre et description (sinon ceux de la page), image Open Graph, `noindex` optionnel. */
public record BioSeo(String title, String description, String imageUrl, boolean noindex) {
  public static final BioSeo NONE = new BioSeo(null, null, null, false);

  public static BioSeo of(String title, String description, String imageUrl, boolean noindex) {
    return new BioSeo(bounded(title, 70), bounded(description, 200), imageUrl == null || imageUrl.isBlank() ? null : BioBlock.httpsUrl(imageUrl), noindex);
  }

  private static String bounded(String raw, int max) {
    if (raw == null || raw.isBlank()) return null;
    String v = raw.strip();
    if (v.length() > max) throw new DomainException("invalid_seo");
    return v;
  }
}
