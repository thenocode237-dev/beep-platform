package io.beep.bo.domain.staff;

import static io.beep.bo.domain.staff.Permission.*;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Rôles staff (specs §3) et droits accordés. Les rôles granulaires complets arrivent en phase 4. */
public enum Role {
  SUPERADMIN(EnumSet.allOf(Permission.class)),
  SUPPORT(EnumSet.of(API_KEYS_REVOKE, WEBHOOKS_REPLAY, USERS_READ, LINKS_READ, PLANS_READ, ENTITLEMENTS_WRITE, METRICS_READ, INVITATIONS_WRITE, BILLING_READ, DOMAINS_READ, CATALOG_READ)),
  FINANCE(EnumSet.of(PLANS_READ, METRICS_READ, BILLING_READ, BILLING_WRITE, PLANS_EDIT)),
  TRUST_SAFETY(EnumSet.of(API_KEYS_REVOKE, PRIVACY_EXPORT, PRIVACY_ERASE, USERS_READ, USERS_SUSPEND, LINKS_READ, LINKS_MODERATE, METRICS_READ, AUDIT_READ, DOMAINS_READ, DOMAINS_MODERATE)),
  CONTENT(EnumSet.of(METRICS_READ, CATALOG_READ, CATALOG_WRITE)),
  READONLY(EnumSet.of(USERS_READ, LINKS_READ, PLANS_READ, METRICS_READ, JOBS_READ, BILLING_READ, DOMAINS_READ, CATALOG_READ));

  private final Set<Permission> permissions;

  Role(Set<Permission> permissions) {
    this.permissions = permissions;
  }

  public boolean can(Permission p) {
    return permissions.contains(p);
  }

  public Set<Permission> permissions() {
    return EnumSet.copyOf(permissions);
  }

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Role fromDb(String v) {
    return valueOf(v.toUpperCase(Locale.ROOT));
  }
}
