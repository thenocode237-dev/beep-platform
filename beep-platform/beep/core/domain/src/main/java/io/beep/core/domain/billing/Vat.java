package io.beep.core.domain.billing;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.Map;

/**
 * TVA incluse dans un prix TTC. Taux en points de base (1 925 = 19,25 %), configurés par pays : ils doivent être
 * validés avec un expert-comptable avant la mise en production (critère de sortie de la phase 2).
 */
public final class Vat {
  private Vat() {}

  public record Split(long net, long vat) {}

  public static Split split(long gross, int rateBp) {
    if (gross < 0 || rateBp < 0) throw new DomainException("invalid_request");
    long vat = Math.round((double) gross * rateBp / (10_000 + rateBp));
    return new Split(gross - vat, vat);
  }

  public static String country(String raw) {
    if (raw == null || !raw.matches("[A-Za-z]{2}")) throw new DomainException("invalid_country");
    return raw.toUpperCase(Locale.ROOT);
  }

  public static int rate(Map<String, Integer> rates, String country) {
    Integer bp = rates.get(country);
    if (bp == null) throw new DomainException("unsupported_country");
    return bp;
  }
}
