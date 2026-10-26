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
  static final String CSP = "default-src 'none'; img-src https: data:; media-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

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
    return bio(p,true);
  }

  String bio(BioPage p,boolean showBadge) {
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
    h.append("</div></main>");
    if(showBadge)h.append("<footer><a href=\"").append(esc(site.url())).append("/\">Propulsé par <strong>beep.</strong></a></footer>");
    h.append("</body></html>");
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
      case VIDEO, AUDIO -> "<figure><figcaption>"+esc(c.get("label"))+"</figcaption><"+b.type().db()+" controls preload=\"none\" style=\"max-width:100%;width:100%\" src=\""+esc(c.get("url"))+"\">"+esc(c.get("label"))+"</"+b.type().db()+"></figure>";
      case EMAIL, FORM -> "<form method=\"post\" action=\"/_b/forms/"+p.id()+"/"+b.id()+"\" style=\"display:grid;gap:.75rem\"><h2>"+esc(c.get("label"))+"</h2><p>"+esc(c.getOrDefault("description",""))+"</p><label for=\"email-"+b.id()+"\">E-mail</label><input id=\"email-"+b.id()+"\" name=\"email\" type=\"email\" autocomplete=\"email\" maxlength=\"254\" required style=\"font:inherit;min-height:48px\">"+(b.type()==BioBlock.Type.FORM?"<label for=\"message-"+b.id()+"\">Message</label><textarea id=\"message-"+b.id()+"\" name=\"message\" maxlength=\"2000\" rows=\"4\" required style=\"font:inherit\"></textarea>":"")+"<div hidden><label>Website<input name=\"website\" tabindex=\"-1\" autocomplete=\"off\"></label></div><label><input type=\"checkbox\" name=\"consent\" value=\"true\" required> "+esc(c.get("consentText"))+"</label><a href=\""+esc(c.get("privacyUrl"))+"\" rel=\"noopener noreferrer\">Confidentialité</a><button type=\"submit\" style=\"font:inherit;min-height:48px;cursor:pointer\">Envoyer</button></form>";
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

  String expired(String lang) {
    boolean en = "en".equals(lang);
    return simple(lang, en ? "Link expired" : "Lien expiré", en ? "This link was only valid until a set date and no longer redirects." : "Ce lien n'était valable que jusqu'à une date donnée et ne redirige plus.",
        en ? "Discover Beep" : "Découvrir Beep");
  }

  /** Formulaire de mot de passe : sans script, envoi vers la même adresse (POST). */
  String password(String lang, boolean wrong) {
    boolean en = "en".equals(lang);
    String title = en ? "Protected link" : "Lien protégé";
    return "<!doctype html><html lang=\"" + lang + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<meta name=\"robots\" content=\"noindex\"><title>" + esc(title) + " · Beep</title><style>" + PAGE_CSS
        + "form{display:grid;gap:.75rem;text-align:left}label{font-weight:600}input{min-height:48px;padding:0 .75rem;border-radius:8px;border:1px solid #8F8878;font:inherit;background:#fff;color:#2B2922}"
        + "button{min-height:48px;border:0;border-radius:8px;background:#F0CF65;color:#2B2922;font:inherit;font-weight:600;cursor:pointer}"
        + ".err{color:#9B2C1B;font-weight:600;margin:0 0 .75rem}</style></head><body><main><div class=\"mark\">beep<span>.</span></div><h1>" + esc(title) + "</h1><p>"
        + esc(en ? "Enter the password to continue." : "Saisissez le mot de passe pour continuer.") + "</p>"
        + (wrong ? "<p class=\"err\" role=\"alert\">" + esc(en ? "Incorrect password." : "Mot de passe incorrect.") + "</p>" : "")
        + "<form method=\"post\" action=\"\"><label for=\"p\">" + esc(en ? "Password" : "Mot de passe") + "</label>"
        + "<input id=\"p\" name=\"password\" type=\"password\" autocomplete=\"off\" required maxlength=\"200\" autofocus><button type=\"submit\">"
        + esc(en ? "Continue" : "Continuer") + "</button></form></main></body></html>";
  }

  /**
   * Page intermédiaire d'ouverture d'application (iOS et navigateurs intégrés). Le script, protégé par un nonce, tente d'ouvrir l'application puis,
   * hors navigateur intégré, bascule sur l'adresse web si la page reste visible. Dans un navigateur intégré, rien n'est automatique : l'aide à la sortie
   * est affichée avec les deux boutons.
   */
  String openApp(String lang, io.beep.core.app.redirect.Outcome.Deeplink d, String nonce) {
    boolean en = "en".equals(lang);
    String app = d.plan().appName();
    String open = "android".equals(d.os()) ? d.plan().androidUrl() : d.plan().iosUrl();
    String web = d.plan().webUrl();
    String inApp = d.inApp() ? browserName(d.browser()) : null;
    StringBuilder h = new StringBuilder(2048);
    h.append("<!doctype html><html lang=\"").append(lang).append("\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<meta name=\"robots\" content=\"noindex\"><title>").append(esc(en ? "Opening " : "Ouverture de ")).append(esc(app)).append(" · Beep</title><style>").append(PAGE_CSS)
        .append("a.alt{background:transparent;color:inherit;border:1px solid #8F8878;margin-top:.75rem}.help{background:rgba(240,207,101,.25);border-radius:8px;padding:.75rem 1rem;margin:0 0 1.25rem;text-align:left}")
        .append("</style></head><body><main><div class=\"mark\">beep<span>.</span></div><h1>").append(esc(en ? "Open in " + app : "Ouvrir dans " + app)).append("</h1>");
    if (inApp != null) {
      h.append("<p class=\"help\">").append(esc(en ? "You are in the " + inApp + " in-app browser, which can block apps. Tap the button below, or open this page in your browser (⋯ menu → “Open in browser”)."
          : "Vous êtes dans le navigateur intégré de " + inApp + ", qui peut bloquer l'ouverture des applications. Appuyez sur le bouton ci-dessous, ou ouvrez cette page dans votre navigateur (menu ⋯ → « Ouvrir dans le navigateur »).")).append("</p>");
    } else {
      h.append("<p>").append(esc(en ? "Opening the app…" : "Ouverture de l'application…")).append("</p>");
    }
    if (open != null) h.append("<a id=\"open\" href=\"").append(esc(open)).append("\">").append(esc(en ? "Open " + app : "Ouvrir " + app)).append("</a>");
    h.append("<br><a class=\"alt\" href=\"").append(esc(web)).append("\" rel=\"noopener\">").append(esc(en ? "Continue on the web" : "Continuer sur le web")).append("</a></main>");
    if (open != null) {
      h.append("<script nonce=\"").append(nonce).append("\">var o=").append(js(open)).append(",w=").append(js(web)).append(",inapp=").append(inApp != null)
          .append(";setTimeout(function(){location.href=o},60);if(!inapp){setTimeout(function(){if(!document.hidden)location.replace(w)},1800)}</script>");
    } else if (inApp == null) {
      h.append("<script nonce=\"").append(nonce).append("\">location.replace(").append(js(web)).append(")</script>");
    }
    return h.append("</body></html>").toString();
  }

  static String openAppCsp(String nonce) {
    return "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-" + nonce + "'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
  }

  /** Chaîne JavaScript sûre dans un bloc <script> : guillemets, barres, chevrons et séparateurs de ligne échappés. */
  static String js(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (char ch : s.toCharArray()) {
      switch (ch) {
        case '"' -> b.append("\\u0022");
        case '\\' -> b.append("\\\\");
        case '<' -> b.append("\\u003c");
        case '>' -> b.append("\\u003e");
        case '&' -> b.append("\\u0026");
        case '\'' -> b.append("\\u0027");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\u2028' -> b.append("\\u2028");
        case '\u2029' -> b.append("\\u2029");
        default -> b.append(ch);
      }
    }
    return b.append('"').toString();
  }

  private static String browserName(String browser) {
    return switch (browser) {
      case "instagram" -> "Instagram";
      case "facebook" -> "Facebook";
      case "tiktok" -> "TikTok";
      case "linkedin" -> "LinkedIn";
      case "snapchat" -> "Snapchat";
      default -> browser;
    };
  }

  private static final String PAGE_CSS = "body{margin:0;min-height:100dvh;display:grid;place-items:center;background:#FFFCF3;color:#2B2922;font:16px/1.6 system-ui,sans-serif;padding:1rem}"
      + "main{max-width:28rem;width:100%;text-align:center}h1{font-size:1.6rem;margin:0 0 .5rem}p{color:#625C4C;margin:0 0 1.25rem}"
      + "a{display:inline-flex;align-items:center;justify-content:center;min-height:48px;padding:0 1.25rem;border-radius:8px;background:#F0CF65;color:#2B2922;text-decoration:none;font-weight:600}"
      + "a:focus-visible,button:focus-visible,input:focus-visible{outline:3px solid #2B2922;outline-offset:3px}.mark{font-weight:800;font-size:1.4rem;margin-bottom:1.5rem}.mark span{color:#826006}"
      + "@media (prefers-color-scheme:dark){body{background:#1C1B16;color:#FFFCF3}p{color:#C6C0AF}a{background:#F0CF65;color:#1C1B16}.mark span{color:#F0CF65}}";

  private String simple(String lang, String title, String text, String cta) {
    return "<!doctype html><html lang=\"" + lang + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<meta name=\"robots\" content=\"noindex\"><title>" + esc(title) + " · Beep</title><style>"
        + "body{margin:0;min-height:100dvh;display:grid;place-items:center;background:#FFFFFF;color:#192B46;font:16px/1.6 system-ui,sans-serif;padding:1rem}"
        + "main{max-width:28rem;text-align:center}h1{font-size:1.6rem;margin:0 0 .5rem}p{color:#4C607D;margin:0 0 1.5rem}"
        + "a{display:inline-flex;align-items:center;min-height:48px;padding:0 1.25rem;border-radius:8px;background:#2457D6;color:#fff;text-decoration:none;font-weight:600}"
        + "a:focus-visible{outline:3px solid #192B46;outline-offset:3px}.mark{font-weight:800;font-size:1.4rem;margin-bottom:1.5rem}.mark span{color:#2457D6}"
        + "@media (prefers-color-scheme:dark){body{background:#101D32;color:#F5F8FF}p{color:#B9C8DE}a{background:#A8CAFF;color:#101D32}a:focus-visible{outline-color:#F5F8FF}.mark span{color:#A8CAFF}}"
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
