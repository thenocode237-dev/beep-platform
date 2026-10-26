"use client";

import { WorkspacePicker } from "@/components/WorkspacePicker";
import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { Icon, Logo, type IconName } from "@beep/ui";
import { post } from "@/lib/api";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";
import { href, type Locale } from "@/lib/i18n";

/** Coquille de l'espace client : barre latérale dès 1024 px, barre inférieure fixe sur mobile (cibles ≥ 44 px). */
export function AppShell({
  t,
  locale,
  email,
  brandName="Beep",
  brandColor,
  logoutLabel,
  children,
}: {
  t: ConsoleDict;
  locale: Locale;
  email: string;
  brandName?:string;
  brandColor?:string;
  logoutLabel: string;
  children: ReactNode;
}) {
  const path = usePathname();
  const items: { path: string; label: string; icon: IconName }[] = [
    { path: "/app", label: t.nav.overview, icon: "chart" },
    { path: "/app/links", label: t.nav.links, icon: "link" },
    { path: "/app/qr", label: t.nav.qr, icon: "qr" },
    { path: "/app/bio", label: t.nav.bio, icon: "bio" },
    { path: "/app/domains", label: t.nav.domains, icon: "globe" },
    {
      path: "/app/developer",
      label: locale === "en" ? "Conversions & API" : "Conversions & API",
      icon: "chart",
    },
    {
      path: "/app/team",
      label: locale === "en" ? "Team" : "Équipe",
      icon: "users",
    },
    {
      path: "/app/organization",
      label: locale === "en" ? "Organization" : "Organisation",
      icon: "link",
    },
    {
      path: "/app/integrations",
      label: locale === "en" ? "Integrations" : "Intégrations",
      icon: "settings",
    },
    {
      path: "/app/radar",
      label: locale === "en" ? "Radar" : "Radar",
      icon: "chart",
    },
    {
      path: "/app/insights",
      label: locale === "en" ? "Customer insights" : "Customer insights",
      icon: "chart",
    },
    {
      path: "/app/affiliates",
      label: locale === "en" ? "Affiliates" : "Affiliation",
      icon: "bag",
    },
    {
      path: "/app/bio-submissions",
      label: locale === "en" ? "Bio responses" : "Réponses bio",
      icon: "mail",
    },
    { path:"/app/enterprise", label:locale==="en"?"Enterprise":"Entreprise",icon:"shield" },
    { path: "/app/billing", label: t.nav.billing, icon: "bag" },
    { path: "/app/settings", label: t.nav.settings, icon: "settings" },
  ];
  const current = (p: string) => {
    const full = href(locale, p);
    return p === "/app" ? path === full : path.startsWith(full);
  };
  const nav = items.map((i) => (
    <a
      key={i.path}
      href={href(locale, i.path)}
      className="app-nav__item"
      aria-current={current(i.path) ? "page" : undefined}
    >
      <Icon name={i.icon} size={21} />
      <span>{i.label}</span>
    </a>
  ));
  const mobileNav = [
    ...nav.slice(0, 4),
    <a
      key="more"
      href={href(locale, "/app/menu")}
      className="app-nav__item"
      aria-current={
        path.endsWith("/app/menu") ||
        items.slice(4).some((i) => current(i.path))
          ? "page"
          : undefined
      }
    >
      <Icon name="settings" size={21} />
      <span>{locale === "en" ? "More" : "Plus"}</span>
    </a>,
  ];
  return (
    <div className="app-shell" style={brandColor?{borderTop:"4px solid "+brandColor}:undefined}>
      <aside className="app-sidebar" aria-label={t.nav.label}>
        <Logo href={href(locale, "/app")} label={brandName} />
        <WorkspacePicker en={locale === "en"} />
        <nav className="app-nav">{nav}</nav>
        <div className="app-sidebar__account">
          <span className="app-avatar" aria-hidden="true">
            {email.charAt(0).toUpperCase()}
          </span>
          <span className="app-sidebar__email">{email}</span>
        </div>
        <button
          type="button"
          className="app-nav__item"
          onClick={async () => {
            await post("/auth/logout");
            window.location.assign(href(locale, "/login"));
          }}
        >
          <Icon name="logout" size={21} />
          <span>{logoutLabel}</span>
        </button>
      </aside>
      <header className="app-topbar">
        <Logo href={href(locale, "/app")} label={brandName} />
        <WorkspacePicker en={locale === "en"} />
      </header>
      <main id="main" className="app-main">
        {children}
      </main>
      <nav className="app-bottombar" aria-label={t.nav.label}>
        {mobileNav}
      </nav>
    </div>
  );
}
