package io.beep.core.domain.qr;

import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.user.Email;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contenu d'un QR code statique : encodé tel quel dans l'image (aucune redirection, donc ni statistiques ni modification
 * après impression). Formats reconnus par les lecteurs courants (WIFI:, MATMSG/mailto:, SMSTO:, tel:, vCard 3.0).
 */
public record QrContent(Type type, Map<String, String> fields) {
  public static final int MAX_ENCODED = 1200;
  private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ().-]{4,20}$");

  public enum Type {
    URL(Set.of("url")),
    TEXT(Set.of("text")),
    WIFI(Set.of("ssid", "password", "security", "hidden")),
    EMAIL(Set.of("to", "subject", "body")),
    SMS(Set.of("phone", "message")),
    PHONE(Set.of("phone")),
    VCARD(Set.of("name", "phone", "email", "org", "url"));

    final Set<String> allowed;

    Type(Set<String> allowed) {
      this.allowed = allowed;
    }

    public String db() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Type fromDb(String v) {
      try {
        return valueOf(v.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new DomainException("invalid_qr");
      }
    }
  }

  public QrContent {
    fields = Map.copyOf(fields);
  }

  /** Valide et normalise les champs saisis ; les champs inconnus sont refusés. */
  public static QrContent of(Type type, Map<String, String> raw) {
    if (type == null || raw == null) throw new DomainException("invalid_qr");
    Map<String, String> f = new LinkedHashMap<>();
    for (var e : raw.entrySet()) {
      if (!type.allowed.contains(e.getKey())) throw new DomainException("invalid_qr");
      if (e.getValue() != null && !e.getValue().isBlank()) f.put(e.getKey(), e.getValue().strip());
    }
    switch (type) {
      case URL -> f.put("url", Destination.parse(required(f, "url")).url());
      case TEXT -> bounded(required(f, "text"), 1000);
      case WIFI -> {
        bounded(required(f, "ssid"), 32);
        String sec = f.getOrDefault("security", "WPA").toUpperCase(Locale.ROOT);
        if (!Set.of("WPA", "WEP", "NOPASS").contains(sec)) throw new DomainException("invalid_qr");
        f.put("security", sec);
        if (!sec.equals("NOPASS")) bounded(required(f, "password"), 63);
        else f.remove("password");
        f.put("hidden", Boolean.toString("true".equalsIgnoreCase(f.get("hidden"))));
      }
      case EMAIL -> {
        f.put("to", new Email(required(f, "to")).value());
        if (f.containsKey("subject")) bounded(f.get("subject"), 200);
        if (f.containsKey("body")) bounded(f.get("body"), 600);
      }
      case SMS -> {
        phone(required(f, "phone"));
        if (f.containsKey("message")) bounded(f.get("message"), 300);
      }
      case PHONE -> phone(required(f, "phone"));
      case VCARD -> {
        bounded(required(f, "name"), 100);
        if (f.containsKey("phone")) phone(f.get("phone"));
        if (f.containsKey("email")) f.put("email", new Email(f.get("email")).value());
        if (f.containsKey("org")) bounded(f.get("org"), 100);
        if (f.containsKey("url")) f.put("url", Destination.parse(f.get("url")).url());
      }
    }
    QrContent content = new QrContent(type, f);
    if (content.encode().length() > MAX_ENCODED) throw new DomainException("invalid_qr");
    return content;
  }

  /** Chaîne encodée dans le QR code. */
  public String encode() {
    return switch (type) {
      case URL -> fields.get("url");
      case TEXT -> fields.get("text");
      case WIFI -> "WIFI:T:" + (fields.get("security").equals("NOPASS") ? "nopass" : fields.get("security")) + ";S:" + wifi(fields.get("ssid"))
          + (fields.containsKey("password") ? ";P:" + wifi(fields.get("password")) : "") + (Boolean.parseBoolean(fields.get("hidden")) ? ";H:true" : "") + ";;";
      case EMAIL -> "mailto:" + fields.get("to") + query("subject", fields.get("subject"), "body", fields.get("body"));
      case SMS -> "SMSTO:" + fields.get("phone") + ":" + fields.getOrDefault("message", "");
      case PHONE -> "tel:" + fields.get("phone").replaceAll("[ ().-]", "");
      case VCARD -> {
        StringBuilder b = new StringBuilder("BEGIN:VCARD\nVERSION:3.0\n");
        b.append("N:").append(vcard(fields.get("name"))).append(";;;;\n");
        b.append("FN:").append(vcard(fields.get("name"))).append('\n');
        if (fields.containsKey("org")) b.append("ORG:").append(vcard(fields.get("org"))).append('\n');
        if (fields.containsKey("phone")) b.append("TEL:").append(vcard(fields.get("phone"))).append('\n');
        if (fields.containsKey("email")) b.append("EMAIL:").append(vcard(fields.get("email"))).append('\n');
        if (fields.containsKey("url")) b.append("URL:").append(vcard(fields.get("url"))).append('\n');
        yield b.append("END:VCARD").toString();
      }
    };
  }

  private static String required(Map<String, String> f, String key) {
    String v = f.get(key);
    if (v == null || v.isBlank()) throw new DomainException("invalid_qr");
    return v;
  }

  private static void bounded(String v, int max) {
    if (v.length() > max) throw new DomainException("invalid_qr");
  }

  private static void phone(String v) {
    if (!PHONE.matcher(v).matches()) throw new DomainException("invalid_qr");
  }

  private static String wifi(String v) {
    return v.replaceAll("([\\\\;,:\"])", "\\\\$1");
  }

  private static String vcard(String v) {
    return v.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
  }

  private static String query(String k1, String v1, String k2, String v2) {
    StringBuilder q = new StringBuilder();
    if (v1 != null) q.append(q.isEmpty() ? "?" : "&").append(k1).append('=').append(enc(v1));
    if (v2 != null) q.append(q.isEmpty() ? "?" : "&").append(k2).append('=').append(enc(v2));
    return q.toString();
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
