package io.beep.core.adapter.in.http;

import io.beep.core.app.common.PublicSite;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.bio.BioTheme;
import java.util.Map;

/**
 * Pages HTML servies par le core : page bio (rendu serveur, CSS en ligne, aucun script, quelques Ko), lien introuvable,
 * lien indisponible. Toute donnée saisie est échappée ; les URL ont été validées (http/https) par le domaine.
 */
final class PublicPages {
  /** Aucun script, aucun cadre ; images https des pages bio autorisées. */
  static final String CSP = "default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

  private record Palette(String bg, String text, String muted, String card, String border, String buttonBg, String buttonText, String scheme) {}

  /** Paires texte/fond conformes WCAG AA (≥ 4,5:1), reprises de la charte Atelier. */
  private static final Map<BioTheme, Palette> PALETTES = Map.of(
      BioTheme.IVOIRE, new Palette("#F8F7F2", "#222B25", "#58634F", "#FFFFFF", "#DCDED5", "#BD3516", "#FFFFFF", "light"),
      BioTheme.FORET, new Palette("#243D30", "#F8F7F2", "#BCD0BA", "#2E4A3B", "#4A6655", "#FFAD86", "#222B25", "dark"),
      BioTheme.NUIT, new Palette("#18231C", "#F8F7F2", "#B7C2B4", "#222B25", "#3E5143", "#F8F7F2", "#18231C", "dark"),
      BioTheme.SOLEIL, new Palette("#F5E8DD", "#222B25", "#58634F", "#FFFFFF", "#E2CBB8", "#243D30", "#F8F7F2", "light"));

  private static final Map<String, String> NETWORKS = Map.ofEntries(Map.entry("instagram", "Instagram"), Map.entry("tiktok", "TikTok"),
      Map.entry("facebook", "Facebook"), Map.entry("x", "X"), Map.entry("youtube", "YouTube"), Map.entry("linkedin", "LinkedIn"),
      Map.entry("whatsapp", "WhatsApp"), Map.entry("telegram", "Telegram"), Map.entry("github", "GitHub"), Map.entry("website", "Site web"),
      Map.entry("email", "E-mail"));

  private final PublicSite site;

  PublicPages(PublicSite site) {
    this.site = site;
  }

  static String language(String acceptLanguage) {
    return acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en") ? "en" : "fr";
  }

  String bio(BioPage p) {
    Palette c = PALETTES.get(p.theme());
    String title = p.seo().title() != null ? p.seo().title() : p.title();
    String description = p.seo().description() != null ? p.seo().description() : p.description();
    String url = site.shortUrl(p.slug().value());
    StringBuilder h = new StringBuilder(4096);
    h.append("<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<title>").append(esc(title)).append("</title>")
        .append("<link rel=\"canonical\" href=\"").append(esc(url)).append("\">")
        .append("<meta name=\"color-scheme\" content=\"").append(c.scheme()).append("\"><meta name=\"theme-color\" content=\"").append(c.bg()).append("\">");
    if (description != null) h.append("<meta name=\"description\" content=\"").append(esc(description)).append("\">");
    if (p.seo().noindex()) h.append("<meta name=\"robots\" content=\"noindex,nofollow\">");
    h.append("<meta property=\"og:type\" content=\"profile\"><meta property=\"og:title\" content=\"").append(esc(title)).append("\">")
        .append("<meta property=\"og:url\" content=\"").append(esc(url)).append("\">");
    if (description != null) h.append("<meta property=\"og:description\" content=\"").append(esc(description)).append("\">");
    String image = p.seo().imageUrl() != null ? p.seo().imageUrl() : p.avatarUrl();
    if (image != null) h.append("<meta property=\"og:image\" content=\"").append(esc(image)).append("\"><meta name=\"twitter:card\" content=\"summary\">");
    h.append("<style>").append(css(c)).append("</style></head><body><main>");
    h.append("<header>");
    if (p.avatarUrl() != null) h.append("<img class=\"avatar\" src=\"").append(esc(p.avatarUrl())).append("\" alt=\"\" width=\"96\" height=\"96\">");
    h.append("<h1>").append(esc(p.title())).append("</h1>");
    if (p.description() != null) h.append("<p class=\"bio\">").append(esc(p.description())).append("</p>");
    h.append("</header><div class=\"blocks\">");
    for (BioBlock b : p.blocks()) h.append(block(p, b));
    h.append("</div></main><footer><a href=\"").append(esc(site.url())).append("/\">Propulsé par <strong>beep.</strong></a></footer></body></html>");
    return h.toString();
  }

  private String block(BioPage p, BioBlock b) {
    Map<String, String> c = b.config();
    String track = "/_b/c/" + p.id() + "/" + b.id();
    return switch (b.type()) {
      case LINK -> "<a class=\"link\" href=\"" + esc(track) + "\" rel=\"noopener\">" + esc(c.get("label")) + "</a>";
      case BUTTON -> "<a class=\"button\" href=\"" + esc(track) + "\" rel=\"noopener\">" + esc(c.get("label")) + "</a>";
      case HEADING -> "<h2>" + esc(c.get("text")) + "</h2>";
      case TEXT -> "<p class=\"text\">" + esc(c.get("text")).replace("\n", "<br>") + "</p>";
      case IMAGE -> {
        String img = "<img class=\"image\" src=\"" + esc(c.get("url")) + "\" alt=\"" + esc(c.getOrDefault("alt", "")) + "\" loading=\"lazy\">";
        yield c.containsKey("href") ? "<a href=\"" + esc(track) + "\" rel=\"noopener\">" + img + "</a>" : img;
      }
      case SOCIAL -> {
        StringBuilder s = new StringBuilder("<ul class=\"social\">");
        for (String network : BioBlock.SOCIAL_NETWORKS) {
          String v = c.get(network);
          if (v == null) continue;
          String href = network.equals("email") ? "mailto:" + v : v;
          s.append("<li><a href=\"").append(esc(href)).append("\" rel=\"noopener me\">").append(NETWORKS.get(network)).append("</a></li>");
        }
        yield s.append("</ul>").toString();
      }
    };
  }

  private static String css(Palette c) {
    return "*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;min-height:100dvh;background:" + c.bg() + ";color:" + c.text()
        + ";font:16px/1.6 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;display:flex;flex-direction:column;align-items:center}"
        + "main{width:100%;max-width:34rem;padding:2.5rem 1rem 1.5rem}header{text-align:center;margin-bottom:1.5rem}"
        + ".avatar{width:96px;height:96px;border-radius:50%;object-fit:cover;border:2px solid " + c.border() + "}"
        + "h1{font-size:1.5rem;line-height:1.25;margin:.75rem 0 .25rem}.bio{color:" + c.muted() + ";margin:0}"
        + ".blocks{display:flex;flex-direction:column;gap:.75rem}h2{font-size:1.1rem;margin:1rem 0 0;text-align:center}.text{margin:0;color:" + c.muted() + ";text-align:center}"
        + ".link,.button{display:flex;align-items:center;justify-content:center;min-height:52px;padding:.75rem 1rem;border-radius:8px;text-decoration:none;font-weight:600;text-align:center}"
        + ".link{background:" + c.card() + ";color:" + c.text() + ";border:1px solid " + c.border() + "}"
        + ".button{background:" + c.buttonBg() + ";color:" + c.buttonText() + "}.link:hover,.button:hover{filter:brightness(.96)}"
        + "a:focus-visible{outline:3px solid " + c.buttonBg() + ";outline-offset:3px}"
        + ".image{display:block;width:100%;height:auto;border-radius:8px}"
        + ".social{list-style:none;display:flex;flex-wrap:wrap;justify-content:center;gap:.5rem;margin:.5rem 0;padding:0}"
        + ".social a{display:inline-flex;align-items:center;min-height:44px;padding:0 .9rem;border-radius:999px;border:1px solid " + c.border() + ";color:" + c.text()
        + ";text-decoration:none;font-size:.9rem}footer{padding:1.5rem 1rem 2rem}footer a{color:" + c.muted() + ";font-size:.85rem;text-decoration:none}"
        + "@media (prefers-reduced-motion:reduce){*{transition:none!important}}";
  }

  String notFound(String lang) {
    boolean en = "en".equals(lang);
    return simple(lang, en ? "Link not found" : "Lien introuvable", en ? "This link doesn't exist or has been removed." : "Ce lien n'existe pas ou a été supprimé.",
        en ? "Discover Beep" : "Découvrir Beep");
  }

  String unavailable(String lang) {
    boolean en = "en".equals(lang);
    return simple(lang, en ? "Link unavailable" : "Lien indisponible",
        en ? "This link has been disabled and no longer redirects. If you think this is a mistake, contact its owner."
            : "Ce lien a été désactivé et ne redirige plus. Si vous pensez qu'il s'agit d'une erreur, contactez la personne qui l'a partagé.",
        en ? "Discover Beep" : "Découvrir Beep");
  }

  private String simple(String lang, String title, String text, String cta) {
    return "<!doctype html><html lang=\"" + lang + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<meta name=\"robots\" content=\"noindex\"><title>" + esc(title) + " · Beep</title><style>"
        + "body{margin:0;min-height:100dvh;display:grid;place-items:center;background:#F8F7F2;color:#222B25;font:16px/1.6 system-ui,sans-serif;padding:1rem}"
        + "main{max-width:28rem;text-align:center}h1{font-size:1.6rem;margin:0 0 .5rem}p{color:#58634F;margin:0 0 1.5rem}"
        + "a{display:inline-flex;align-items:center;min-height:48px;padding:0 1.25rem;border-radius:8px;background:#BD3516;color:#fff;text-decoration:none;font-weight:600}"
        + "a:focus-visible{outline:3px solid #222B25;outline-offset:3px}.mark{font-weight:800;font-size:1.4rem;margin-bottom:1.5rem}.mark span{color:#BD3516}"
        + "@media (prefers-color-scheme:dark){body{background:#18231C;color:#F8F7F2}p{color:#B7C2B4}a{background:#FFAD86;color:#18231C}a:focus-visible{outline-color:#F8F7F2}.mark span{color:#FFAD86}}"
        + "</style></head><body><main><div class=\"mark\">beep<span>.</span></div><h1>" + esc(title) + "</h1><p>" + esc(text) + "</p><a href=\""
        + esc(site.url()) + ("en".equals(lang) ? "/en" : "/") + "\">" + esc(cta) + "</a></main></body></html>";
  }

  static String esc(String s) {
    if (s == null) return "";
    StringBuilder b = new StringBuilder(s.length() + 16);
    for (char ch : s.toCharArray()) {
      switch (ch) {
        case '&' -> b.append("&amp;");
        case '<' -> b.append("&lt;");
        case '>' -> b.append("&gt;");
        case '"' -> b.append("&quot;");
        case '\'' -> b.append("&#39;");
        default -> b.append(ch);
      }
    }
    return b.toString();
  }
}
