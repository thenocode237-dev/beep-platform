package io.beep.core.domain.customdomain;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Nom d'hôte d'un domaine personnalisé : FQDN en minuscules, ni adresse IP, ni joker, ni le site de Beepclick ou l'un de ses sous-domaines. */
public final class Hostname {
  private Hostname() {}

  private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
  private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

  public static String parse(String raw, String siteHost) {
    if (raw == null) throw new DomainException("invalid_hostname");
    String h = raw.strip().toLowerCase(Locale.ROOT);
    if (h.startsWith("https://")) h = h.substring(8);
    else if (h.startsWith("http://")) h = h.substring(7);
    if (h.endsWith("/")) h = h.substring(0, h.length() - 1);
    if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
    if (h.isEmpty() || h.length() > 253 || IPV4.matcher(h).matches() || h.contains(":") || h.contains("/")) throw new DomainException("invalid_hostname");
    String[] labels = h.split("\\.", -1);
    if (labels.length < 2) throw new DomainException("invalid_hostname"); // « localhost » et noms sans suffixe
    for (String label : labels) if (!LABEL.matcher(label).matches()) throw new DomainException("invalid_hostname");
    if (labels[labels.length - 1].chars().allMatch(Character::isDigit)) throw new DomainException("invalid_hostname");
    String site = siteHost == null ? "" : siteHost.toLowerCase(Locale.ROOT);
    if (!site.isEmpty() && (h.equals(site) || h.endsWith("." + site))) throw new DomainException("reserved_hostname");
    if (h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) throw new DomainException("reserved_hostname");
    return h;
  }
}
