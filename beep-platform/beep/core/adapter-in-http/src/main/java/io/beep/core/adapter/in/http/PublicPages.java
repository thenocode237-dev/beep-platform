package io.beep.core.adapter.in.http;

import io.beep.core.app.common.PublicSite;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioColors;
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

  private record Palette(String bg, String text, String muted, String card, String border, String buttonBg, String buttonText, String scheme, String accent) {}

  /** Paires texte/fond conformes WCAG AA (≥ 4,5:1), reprises de la charte Atelier. */
  private static final Map<BioTheme, Palette> PALETTES = Map.of(
      BioTheme.IVOIRE, new Palette("#F3F7FF", "#192B46", "#4C607D", "#FFFFFF", "#CBD7EA", "#1F3560", "#FFFFFF", "light", null),
      BioTheme.FORET, new Palette("#243D30", "#F8F7F2", "#BCD0BA", "#2E4A3B", "#4A6655", "#FFAD86", "#222B25", "dark", null),
      BioTheme.NUIT, new Palette("#101D32", "#F5F8FF", "#B9C8DE", "#1A2B47", "#2F4468", "#F5F8FF", "#101D32", "dark", null),
      BioTheme.SOLEIL, new Palette("#F5E8DD", "#222B25", "#58634F", "#FFFFFF", "#E2CBB8", "#243D30", "#F8F7F2", "light", null));

  private static final Map<String, String> NETWORKS = Map.ofEntries(Map.entry("instagram", "Instagram"), Map.entry("tiktok", "TikTok"),
      Map.entry("facebook", "Facebook"), Map.entry("x", "X"), Map.entry("youtube", "YouTube"), Map.entry("linkedin", "LinkedIn"),
      Map.entry("whatsapp", "WhatsApp"), Map.entry("telegram", "Telegram"), Map.entry("github", "GitHub"), Map.entry("website", "Site web"),
      Map.entry("email", "E-mail"));

  private static final String ARROW = "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M5 12h14M13 6l6 6-6 6\"/></svg>";

  /** Logos officiels (Simple Icons, CC0) : « couleur|tracé ». currentColor pour les marques noires, lisibles sur tous les thèmes. */
  private static final Map<String, String> LOGOS = Map.ofEntries(
      Map.entry("instagram", "#E4405F|M7.0301.084c-1.2768.0602-2.1487.264-2.911.5634-.7888.3075-1.4575.72-2.1228 1.3877-.6652.6677-1.075 1.3368-1.3802 2.127-.2954.7638-.4956 1.6365-.552 2.914-.0564 1.2775-.0689 1.6882-.0626 4.947.0062 3.2586.0206 3.6671.0825 4.9473.061 1.2765.264 2.1482.5635 2.9107.308.7889.72 1.4573 1.388 2.1228.6679.6655 1.3365 1.0743 2.1285 1.38.7632.295 1.6361.4961 2.9134.552 1.2773.056 1.6884.069 4.9462.0627 3.2578-.0062 3.668-.0207 4.9478-.0814 1.28-.0607 2.147-.2652 2.9098-.5633.7889-.3086 1.4578-.72 2.1228-1.3881.665-.6682 1.0745-1.3378 1.3795-2.1284.2957-.7632.4966-1.636.552-2.9124.056-1.2809.0692-1.6898.063-4.948-.0063-3.2583-.021-3.6668-.0817-4.9465-.0607-1.2797-.264-2.1487-.5633-2.9117-.3084-.7889-.72-1.4568-1.3876-2.1228C21.2982 1.33 20.628.9208 19.8378.6165 19.074.321 18.2017.1197 16.9244.0645 15.6471.0093 15.236-.005 11.977.0014 8.718.0076 8.31.0215 7.0301.0839m.1402 21.6932c-1.17-.0509-1.8053-.2453-2.2287-.408-.5606-.216-.96-.4771-1.3819-.895-.422-.4178-.6811-.8186-.9-1.378-.1644-.4234-.3624-1.058-.4171-2.228-.0595-1.2645-.072-1.6442-.079-4.848-.007-3.2037.0053-3.583.0607-4.848.05-1.169.2456-1.805.408-2.2282.216-.5613.4762-.96.895-1.3816.4188-.4217.8184-.6814 1.3783-.9003.423-.1651 1.0575-.3614 2.227-.4171 1.2655-.06 1.6447-.072 4.848-.079 3.2033-.007 3.5835.005 4.8495.0608 1.169.0508 1.8053.2445 2.228.408.5608.216.96.4754 1.3816.895.4217.4194.6816.8176.9005 1.3787.1653.4217.3617 1.056.4169 2.2263.0602 1.2655.0739 1.645.0796 4.848.0058 3.203-.0055 3.5834-.061 4.848-.051 1.17-.245 1.8055-.408 2.2294-.216.5604-.4763.96-.8954 1.3814-.419.4215-.8181.6811-1.3783.9-.4224.1649-1.0577.3617-2.2262.4174-1.2656.0595-1.6448.072-4.8493.079-3.2045.007-3.5825-.006-4.848-.0608M16.953 5.5864A1.44 1.44 0 1 0 18.39 4.144a1.44 1.44 0 0 0-1.437 1.4424M5.8385 12.012c.0067 3.4032 2.7706 6.1557 6.173 6.1493 3.4026-.0065 6.157-2.7701 6.1506-6.1733-.0065-3.4032-2.771-6.1565-6.174-6.1498-3.403.0067-6.156 2.771-6.1496 6.1738M8 12.0077a4 4 0 1 1 4.008 3.9921A3.9996 3.9996 0 0 1 8 12.0077"),
      Map.entry("tiktok", "currentColor|M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z"),
      Map.entry("facebook", "#0866FF|M9.101 23.691v-7.98H6.627v-3.667h2.474v-1.58c0-4.085 1.848-5.978 5.858-5.978.401 0 .955.042 1.468.103a8.68 8.68 0 0 1 1.141.195v3.325a8.623 8.623 0 0 0-.653-.036 26.805 26.805 0 0 0-.733-.009c-.707 0-1.259.096-1.675.309a1.686 1.686 0 0 0-.679.622c-.258.42-.374.995-.374 1.752v1.297h3.919l-.386 2.103-.287 1.564h-3.246v8.245C19.396 23.238 24 18.179 24 12.044c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.628 3.874 10.35 9.101 11.647Z"),
      Map.entry("x", "currentColor|M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z"),
      Map.entry("youtube", "#FF0000|M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"),
      Map.entry("linkedin", "#0A66C2|M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"),
      Map.entry("whatsapp", "#25D366|M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"),
      Map.entry("telegram", "#26A5E4|M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z"),
      Map.entry("github", "currentColor|M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"));

  private static final String GLOBE = "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18\"/></svg>";
  private static final String MAIL = "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2\"/><path d=\"m3 7 9 6 9-6\"/></svg>";

  /** Applique les couleurs choisies : secondaire = fond, principale = boutons, accent = contours, flèches et focus. Les textes sont dérivés (contraste ≥ 4,5:1). */
  private static Palette custom(Palette base, BioColors k) {
    String bg = k.secondary() != null ? k.secondary() : base.bg();
    String text = k.secondary() != null ? readable(bg) : base.text();
    String muted = k.secondary() != null ? mix(text, bg, 0.78) : base.muted();
    String card = k.secondary() != null ? bg : base.card();
    String border = k.secondary() != null ? mix(text, bg, 0.22) : base.border();
    String buttonBg = k.primary() != null ? k.primary() : base.buttonBg();
    String buttonText = k.primary() != null ? readable(buttonBg) : base.buttonText();
    String scheme = k.secondary() != null ? (luminance(bg) > 0.4 ? "light" : "dark") : base.scheme();
    return new Palette(bg, text, muted, card, border, buttonBg, buttonText, scheme, k.accent());
  }

  private static double luminance(String hex) {
    double[] c = new double[3];
    for (int i = 0; i < 3; i++) {
      double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
      c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }

  /** Texte noir ou blanc, selon le meilleur contraste sur le fond donné. */
  private static String readable(String bg) {
    double l = luminance(bg);
    return (l + 0.05) / (luminance("#14181F") + 0.05) >= (luminance("#FFFFFF") + 0.05) / (l + 0.05) ? "#14181F" : "#FFFFFF";
  }

  /** Mélange `a` (poids w) et `b`. */
  private static String mix(String a, String b, double w) {
    StringBuilder out = new StringBuilder("#");
    for (int i = 0; i < 3; i++) {
      int x = Integer.parseInt(a.substring(1 + 2 * i, 3 + 2 * i), 16);
      int y = Integer.parseInt(b.substring(1 + 2 * i, 3 + 2 * i), 16);
      out.append(String.format("%02X", (int) Math.round(x * w + y * (1 - w))));
    }
    return out.toString();
  }

  private static String logo(String network) {
    if (network.equals("website")) return GLOBE;
    if (network.equals("email")) return MAIL;
    String[] cd = LOGOS.get(network).split("\\|", 2);
    return "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"" + cd[0] + "\" aria-hidden=\"true\"><path d=\"" + cd[1] + "\"/></svg>";
  }

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
    Palette c = custom(PALETTES.get(p.theme()), p.colors());
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
    boolean cover = p.avatarUrl() != null;
    h.append("<section class=\"hero\">");
    if (cover) h.append("<img class=\"cover\" src=\"").append(esc(p.avatarUrl())).append("\" alt=\"\" width=\"600\" height=\"375\">");
    h.append("<div class=\"id\"><h1>").append(esc(p.title())).append("</h1>");
    if (p.description() != null) h.append("<p class=\"bio\">").append(esc(p.description())).append("</p>");
    h.append("</div></section><div class=\"body\"><div class=\"blocks\">");
    boolean[] primary = {true};
    for (BioBlock b : p.blocks()) if (!b.hidden()) h.append(block(p, b, primary));
    h.append("</div></div></main>");
    if(showBadge)h.append("<footer><a href=\"").append(esc(site.url())).append("/\">Propulsé par <strong>beep.</strong></a></footer>");
    h.append("</body></html>");
    return h.toString();
  }

  private String block(BioPage p, BioBlock b, boolean[] primary) {
    Map<String, String> c = b.config();
    String track = "/_b/c/" + p.id() + "/" + b.id();
    return switch (b.type()) {
      case LINK, BUTTON -> {
        boolean first = primary[0];
        primary[0] = false;
        yield "<a class=\"" + (first || b.type() == BioBlock.Type.BUTTON ? "button" : "link") + "\" href=\"" + esc(track) + "\" rel=\"noopener\"><span>" + esc(c.get("label")) + "</span>" + ARROW + "</a>";
      }
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
          s.append("<li><a href=\"").append(esc(href)).append("\" rel=\"noopener me\" aria-label=\"").append(NETWORKS.get(network)).append("\" title=\"").append(NETWORKS.get(network)).append("\">").append(logo(network)).append("</a></li>");
        }
        yield s.append("</ul>").toString();
      }
    };
  }

  private static String css(Palette c) {
    return "*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;min-height:100dvh;background:" + c.bg() + ";color:" + c.text()
        + ";font:16px/1.6 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;display:flex;flex-direction:column;align-items:center}"
        + "main{width:100%;max-width:28rem;flex:1;padding:0 0 1rem}"
        + ".cover{display:block;width:100%;aspect-ratio:8/5;height:auto;object-fit:cover}"
        + ".id{padding:1.75rem 1rem 0;text-align:center}.body{padding:1.25rem 1rem 1rem}"
        + "h1{font-size:1.6rem;line-height:1.2;letter-spacing:-.01em;margin:0 0 .25rem;text-wrap:balance}.bio{color:" + c.muted() + ";margin:0}"
        + ".blocks{display:flex;flex-direction:column;gap:.75rem}h2{font-size:1.1rem;margin:1rem 0 0;text-align:center}.text{margin:0;color:" + c.muted() + ";text-align:center}"
        + "@media (min-width:56rem){main{max-width:68rem;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,28rem);column-gap:4rem;align-items:center;padding:0 2rem}"
        + ".hero{position:sticky;top:0;align-self:start;min-height:100dvh;display:flex;flex-direction:column;justify-content:center;gap:1.5rem;padding:2rem 0}"
        + ".cover{aspect-ratio:4/5;max-height:70dvh;width:auto;max-width:100%}.id{padding:0;text-align:left}h1{font-size:2.5rem}.bio{font-size:1.125rem}"
        + ".body{padding:2rem 0}.blocks{gap:1rem}.social{justify-content:flex-start!important}}"
        + ".link,.button{display:flex;align-items:center;justify-content:space-between;gap:.75rem;min-height:56px;padding:.75rem 1.1rem;text-decoration:none;font-weight:500;text-align:left}"
        + ".link{background:" + c.card() + ";color:" + c.text() + ";border:1px solid " + (c.accent() != null ? c.accent() : c.border()) + "}"
        + ".button{background:" + c.buttonBg() + ";color:" + c.buttonText() + ";border:1px solid " + c.buttonBg() + "}.link:hover,.button:hover{filter:brightness(.96)}"
        + ".link svg,.button svg{flex:none}" + (c.accent() != null ? ".link svg{color:" + c.accent() + "}" : "") + "a:focus-visible{outline:3px solid " + (c.accent() != null ? c.accent() : c.buttonBg()) + ";outline-offset:3px}"
        + ".image{display:block;width:100%;height:auto}"
        + ".social{list-style:none;display:flex;flex-wrap:wrap;justify-content:center;gap:.25rem;margin:.5rem 0;padding:0}"
        + ".social a{display:inline-flex;align-items:center;justify-content:center;width:48px;height:48px;color:" + c.text() + "}.social svg{width:24px;height:24px}footer{padding:1rem 1rem 2rem}footer a{color:" + c.muted() + ";font-size:.85rem;text-decoration:none}"
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
