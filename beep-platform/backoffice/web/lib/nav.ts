import type { IconName } from "@beep/ui";

export type NavItem = { href: string; label: string; icon: IconName; permission: string; mobile?: boolean };

/** Modules affichés selon les droits du rôle (le contrôle réel reste côté API). */
export const navItems: NavItem[] = [
  {href:"/developer",label:"Conversions & API",icon:"chart",permission:"METRICS_READ"},
  {href:"/moderation",label:"Modération",icon:"shield",permission:"LINKS_MODERATE"},
  {href:"/feature-flags",label:"Feature flags",icon:"settings",permission:"FLAGS_READ"},
  { href: "/metrics", label: "Tableau de bord", icon: "chart", permission: "METRICS_READ", mobile: true },
  { href: "/users", label: "Utilisateurs", icon: "users", permission: "USERS_READ", mobile: true },
  { href: "/links", label: "Liens", icon: "link", permission: "LINKS_READ", mobile: true },
  { href: "/invitations", label: "Invitations", icon: "mail", permission: "INVITATIONS_WRITE" },
  { href: "/catalog", label: "Catalogue d'apps", icon: "smartphone", permission: "CATALOG_READ" },
  { href: "/domains", label: "Domaines", icon: "globe", permission: "DOMAINS_READ" },
  { href: "/billing", label: "Facturation", icon: "bag", permission: "BILLING_READ" },
  { href: "/coupons", label: "Coupons", icon: "scroll", permission: "BILLING_READ" },
  { href: "/plans", label: "Plans & droits", icon: "bag", permission: "PLANS_READ" },
  { href: "/jobs", label: "Jobs", icon: "clock", permission: "JOBS_READ" },
  { href: "/audit", label: "Journal d'audit", icon: "scroll", permission: "AUDIT_READ" },
];
