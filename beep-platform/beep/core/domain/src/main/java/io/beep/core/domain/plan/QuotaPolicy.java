package io.beep.core.domain.plan;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Règles de quotas (specs §6.2 et §6.4) :
 *  - stock (Starter, Pro) : ressources actives en même temps ; supprimer libère une place ;
 *  - créations mensuelles (Elite) : compteur remis à zéro chaque période ; l'existant n'est jamais désactivé.
 * Avertissement à 80 %, blocage de la création à 100 %. Les redirections ne dépendent jamais d'un quota.
 */
public final class QuotaPolicy {
  private QuotaPolicy() {}

  public enum Resource {
    LINKS("links"),
    QR("qr"),
    BIO("bio");

    private final String key;

    Resource(String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }

    String stockKey() {
      return this == BIO ? EntitlementKeys.BIO_MAX : key + ".stock_max";
    }

    String monthlyKey() {
      return this == BIO ? null : key + ".monthly_creations";
    }
  }

  public enum Period {
    STOCK,
    MONTHLY;

    public String db() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public enum Level {
    OK,
    WARNING,
    REACHED;

    public String db() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** Usage courant : ressources existantes et créations de la période en cours. */
  public record Usage(long stock, long createdThisPeriod) {}

  public record Status(Resource resource, Period period, long used, long limit) {
    public boolean unlimited() {
      return limit == Entitlements.UNLIMITED;
    }

    public boolean canCreate() {
      return unlimited() || used < limit;
    }

    public Level level() {
      if (unlimited()) return Level.OK;
      if (used >= limit) return Level.REACHED;
      return used * 10 >= limit * 8L ? Level.WARNING : Level.OK;
    }
  }

  public static Status status(Entitlements e, Resource r, Usage usage) {
    String monthlyKey = r.monthlyKey();
    OptionalLong monthly = monthlyKey == null ? OptionalLong.empty() : e.value(monthlyKey);
    if (monthly.isPresent()) return new Status(r, Period.MONTHLY, usage.createdThisPeriod(), monthly.getAsLong());
    return new Status(r, Period.STOCK, usage.stock(), e.value(r.stockKey()).orElse(0));
  }

  public static void requireCreatable(Entitlements e, Resource r, Usage usage) {
    if (!status(e, r, usage).canCreate()) throw new DomainException("quota_exceeded");
  }
}
