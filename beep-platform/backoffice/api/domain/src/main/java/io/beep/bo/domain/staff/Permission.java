package io.beep.bo.domain.staff;

/** Droits fins du staff (specs §3), accordés par rôle. Chaque module de la phase 1 apporte les siens. */
public enum Permission {
  API_KEYS_REVOKE,
  WEBHOOKS_REPLAY,
  USERS_READ,
  PRIVACY_EXPORT,
  PRIVACY_ERASE,
  FLAGS_READ,
  FLAGS_WRITE,
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
  INVITATIONS_WRITE,
  /** Abonnements, paiements, échéances, factures, MRR/ARR (lecture). */
  BILLING_READ,
  /** Paiements manuels, changement de plan manuel, coupons (ré-authentification TOTP exigée). */
  BILLING_WRITE,
  /** Brouillons de plans (édition) ; la publication vers le core exige PLANS_PUBLISH et une ré-authentification TOTP. */
  PLANS_EDIT,
  PLANS_PUBLISH,
  DOMAINS_READ,
  CATALOG_READ,
  /** Publication versionnée du catalogue d'applications (ré-authentification TOTP exigée). */
  CATALOG_WRITE,
  /** Blocage et déblocage d'un domaine personnalisé, relance de sa vérification (motif obligatoire, ré-authentification TOTP pour le blocage). */
  DOMAINS_MODERATE
}
