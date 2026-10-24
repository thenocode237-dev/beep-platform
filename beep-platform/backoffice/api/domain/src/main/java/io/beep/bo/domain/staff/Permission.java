package io.beep.bo.domain.staff;

/** Droits fins du staff (specs §3), accordés par rôle. Chaque module de la phase 1 apporte les siens. */
public enum Permission {
  USERS_READ,
  /** Suspension et réactivation de comptes (ré-authentification TOTP exigée). */
  USERS_SUSPEND,
  LINKS_READ,
  /** Désactivation et réactivation de liens. */
  LINKS_MODERATE,
  PLANS_READ,
  /** Overrides de droits par workspace (ré-authentification TOTP exigée). */
  ENTITLEMENTS_WRITE,
  METRICS_READ,
  JOBS_READ,
  AUDIT_READ,
  /** Invitations à la bêta privée. */
  INVITATIONS_WRITE
}
