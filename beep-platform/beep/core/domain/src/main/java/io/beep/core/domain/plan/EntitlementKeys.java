package io.beep.core.domain.plan;

import java.util.Set;

/**
 * Clés de droits (specs §6.2). Valeurs entières : quota (-1 = illimité) ou fonctionnalité (0/1).
 * Portées par le plan, surchargeables par workspace depuis le back office.
 */
public final class EntitlementKeys {
  private EntitlementKeys() {}

  public static final String LINKS_STOCK_MAX = "links.stock_max";
  public static final String LINKS_MONTHLY_CREATIONS = "links.monthly_creations";
  public static final String QR_STOCK_MAX = "qr.stock_max";
  public static final String QR_MONTHLY_CREATIONS = "qr.monthly_creations";
  public static final String BIO_MAX = "bio.max";
  public static final String DOMAINS_MAX = "domains.max";
  public static final String MEMBERS_MAX = "members.max";
  public static final String API_RPM = "api.rpm";
  public static final String ANALYTICS_RETENTION_DAYS = "analytics.retention_days";
  public static final String FEATURE_ANALYTICS_ADVANCED = "features.analytics_advanced";
  public static final String FEATURE_BIO_BADGE_REMOVABLE = "features.bio_badge_removable";
  /** Export CSV des statistiques : Pro et Elite (grille §6.1) ; activable pour un workspace de la bêta par override. */
  public static final String FEATURE_EXPORT_CSV = "features.export_csv";

  public static final Set<String> ALL = Set.of(LINKS_STOCK_MAX, LINKS_MONTHLY_CREATIONS, QR_STOCK_MAX, QR_MONTHLY_CREATIONS, BIO_MAX, DOMAINS_MAX,
      MEMBERS_MAX, API_RPM, ANALYTICS_RETENTION_DAYS, FEATURE_ANALYTICS_ADVANCED, FEATURE_BIO_BADGE_REMOVABLE, FEATURE_EXPORT_CSV);

  public static boolean isFeature(String key) {
    return key.startsWith("features.");
  }
}
