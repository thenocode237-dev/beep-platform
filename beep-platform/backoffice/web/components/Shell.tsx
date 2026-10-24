"use client";

import { useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { Icon, Logo } from "@beep/ui";
import { post } from "@/lib/client";
import { navItems as items, type NavItem as Item } from "@/lib/nav";

export function Shell({
  email,
  role,
  permissions,
  coreUp,
  children,
}: {
  email: string;
  role: string;
  permissions: string[];
  coreUp: boolean;
  children: ReactNode;
}) {
  const path = usePathname();
  const [busy, setBusy] = useState(false);
  const allowed = items.filter((i) => permissions.includes(i.permission));
  const link = (i: Item) => (
    <a key={i.href} href={i.href} className="nav__item" aria-current={path.startsWith(i.href) ? "page" : undefined}>
      <Icon name={i.icon} size={21} />
      <span>{i.label}</span>
    </a>
  );
  const logout = (
    <button
      type="button"
      className="nav__item"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        await post("/auth/logout");
        window.location.assign("/login");
      }}
    >
      <Icon name="logout" size={21} />
      <span>{busy ? "Déconnexion…" : "Se déconnecter"}</span>
    </button>
  );
  return (
    <div className="shell">
      <aside className="sidebar" aria-label="Navigation principale">
        <div>
          <Logo href="/" label="Beep back office" />
          <p className="sidebar__label">Espace équipe</p>
        </div>
        <nav className="nav">{allowed.map(link)}</nav>
        <div className="sidebar__note">
          <Icon name="shield" size={20} />
          <p>
            Chaque action compte.
            <br />
            <span>Toutes sont inscrites au journal d'audit.</span>
          </p>
        </div>
        <div className="sidebar__account">
          <span className="staff-avatar" aria-hidden="true">
            {email.charAt(0).toUpperCase()}
          </span>
          <p>
            {email}
            <span>{role}</span>
          </p>
        </div>
        {logout}
      </aside>
      <header className="topbar">
        <Logo href="/" label="Beep back office" />
        <span className="topbar__who">Espace équipe</span>
      </header>
      <div className="shell__main">
        <div className="console-context">
          <span>Beep / Administration</span>
          <span>
            <Icon name="shield" size={16} />
            {role}
          </span>
        </div>
        {!coreUp && (
          <div className="degraded" role="status">
            <Icon name="info" size={18} />
            Le service principal est momentanément injoignable. Les données propres au back office (journal d'audit) restent disponibles.
          </div>
        )}
        <main id="main" className="bp-container">
          {children}
        </main>
      </div>
      <nav className="bottombar" aria-label="Navigation principale">
        {allowed.filter((i) => i.mobile).map(link)}
        <a href="/menu" className="nav__item" aria-current={path.startsWith("/menu") ? "page" : undefined}>
          <Icon name="menu" size={21} />
          <span>Plus</span>
        </a>
      </nav>
    </div>
  );
}
