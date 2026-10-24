import type { ReactNode } from "react";
import { Icon } from "@beep/ui";
import { SiteHeader } from "./SiteChrome";
import { ProductPreview } from "./ProductPreview";
import { experience } from "@/lib/experience";
import type { Dict } from "@/lib/dictionaries/fr";
import { href, type Locale } from "@/lib/i18n";

export function AuthShell({
  dict,
  locale,
  path,
  title,
  sub,
  children,
}: {
  dict: Dict;
  locale: Locale;
  path: string;
  title: string;
  sub?: string;
  children: ReactNode;
}) {
  const c = experience(locale);
  return (
    <>
      <SiteHeader dict={dict} locale={locale} path={path} />
      <main id="main" className="auth">
        <div className="bp-container auth__grid">
          <aside className="auth__visual">
            <div>
              <h2>
                {c.authTitle}
                <br />
                <span>{c.authAccent}</span>
              </h2>
              <p>{c.authText}</p>
            </div>
            <ProductPreview locale={locale} compact />
          </aside>
          <div className="auth__content">
            <a className="auth__back" href={href(locale)}>
              <Icon name="arrow-left" size={17} />
              {c.authBack}
            </a>
            <div className="auth__form bp-stack">
              <div className="auth__heading">
                <span className="auth__symbol">
                  <Icon
                    name={path.includes("password") ? "shield" : "link"}
                    size={24}
                  />
                </span>
                <h1>{title}</h1>
                {sub && <p className="muted">{sub}</p>}
              </div>
              {children}
            </div>
            <p className="auth__secure">
              <Icon name="shield" size={16} />
              {c.authSecure}
            </p>
          </div>
        </div>
      </main>
    </>
  );
}
