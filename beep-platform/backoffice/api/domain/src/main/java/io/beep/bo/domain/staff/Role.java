package io.beep.bo.domain.staff;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Rôles staff (specs §3). Phase 0 : rôles de base ; les droits fins arrivent avec chaque module. */
public enum Role {
  SUPERADMIN(EnumSet.allOf(Permission.class)),
  SUPPORT(EnumSet.of(Permission.USERS_READ)),
  FINANCE(EnumSet.noneOf(Permission.class)),
  TRUST_SAFETY(EnumSet.of(Permission.USERS_READ)),
  CONTENT(EnumSet.noneOf(Permission.class)),
  READONLY(EnumSet.of(Permission.USERS_READ));

  private final Set<Permission> permissions;

  Role(Set<Permission> permissions) {
    this.permissions = permissions;
  }

  public boolean can(Permission p) {
    return permissions.contains(p);
  }

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Role fromDb(String v) {
    return valueOf(v.toUpperCase(Locale.ROOT));
  }
}
