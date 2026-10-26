package io.beep.core.domain.bio;

import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.user.Email;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bloc d'une page bio (v1 : lien, bouton, titre, texte, image, réseaux sociaux). La position est l'ordre dans la page.
 * Les blocs programmables, vidéo, audio, formulaires… arrivent en phase 4.
 */
public record BioBlock(String id, Type type, Map<String, String> config) {
  public static final int MAX_BLOCKS = 50;
  private static final Pattern ID = Pattern.compile("^[A-Za-z0-9]{6,16}$");

  public enum Type {
    LINK(Set.of("label", "url")),
    BUTTON(Set.of("label", "url")),
    HEADING(Set.of("text")),
    TEXT(Set.of("text")),
    IMAGE(Set.of("url", "alt", "href")),
    SOCIAL(Set.copyOf(SOCIAL_NETWORKS)),
    VIDEO(Set.of("url","label")), AUDIO(Set.of("url","label")),
    EMAIL(Set.of("label","description","privacyUrl","consentText")), FORM(Set.of("label","description","privacyUrl","consentText"));

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
        throw new DomainException("invalid_block");
      }
    }
  }

  /** Réseaux reconnus, dans l'ordre d'affichage. */
  public static final List<String> SOCIAL_NETWORKS = List.of("instagram", "tiktok", "facebook", "x", "youtube", "linkedin", "whatsapp", "telegram", "github", "website", "email");

  public BioBlock {
    config = Map.copyOf(config);
  }

  public static BioBlock of(String id, Type type, Map<String, String> raw) {
    if (id == null || !ID.matcher(id).matches() || type == null || raw == null) throw new DomainException("invalid_block");
    Map<String, String> c = new LinkedHashMap<>();
    for (var e : raw.entrySet()) {
      if (!type.allowed.contains(e.getKey())) throw new DomainException("invalid_block");
      if (e.getValue() != null && !e.getValue().isBlank()) c.put(e.getKey(), e.getValue().strip());
    }
    switch (type) {
      case LINK, BUTTON -> {
        text(c, "label", 100, true);
        c.put("url", Destination.parse(required(c, "url")).url());
      }
      case HEADING -> text(c, "text", 120, true);
      case TEXT -> text(c, "text", 1000, true);
      case IMAGE -> {
        c.put("url", httpsUrl(required(c, "url")));
        text(c, "alt", 200, false);
        if (c.containsKey("href")) c.put("href", Destination.parse(c.get("href")).url());
      }
      case VIDEO, AUDIO -> {c.put("url",httpsUrl(required(c,"url")));text(c,"label",120,true);}
      case EMAIL, FORM -> {text(c,"label",120,true);text(c,"description",500,false);text(c,"consentText",500,true);c.put("privacyUrl",httpsUrl(required(c,"privacyUrl")));}
      case SOCIAL -> {
        if (c.isEmpty()) throw new DomainException("invalid_block");
        for (String network : List.copyOf(c.keySet())) {
          c.put(network, network.equals("email") ? new Email(c.get(network)).value() : Destination.parse(c.get(network)).url());
        }
      }
    }
    return new BioBlock(id, type, c);
  }

  public static void requireCount(int count) {
    if (count > MAX_BLOCKS) throw new DomainException("too_many_blocks");
  }

  /** Image ou avatar : https uniquement (contenu mixte bloqué par les navigateurs). */
  public static String httpsUrl(String raw) {
    Destination d = Destination.parse(raw);
    if (!d.url().regionMatches(true, 0, "https://", 0, 8)) throw new DomainException("invalid_block");
    return d.url();
  }

  private static String required(Map<String, String> c, String key) {
    String v = c.get(key);
    if (v == null) throw new DomainException("invalid_block");
    return v;
  }

  private static void text(Map<String, String> c, String key, int max, boolean required) {
    String v = c.get(key);
    if (v == null) {
      if (required) throw new DomainException("invalid_block");
      return;
    }
    if (v.length() > max) throw new DomainException("invalid_block");
  }
}
