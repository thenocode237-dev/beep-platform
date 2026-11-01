import type { ReactNode } from "react";
import { SiteHeader, SiteFooter } from "@/components/SiteChrome";
import { getDictionary, type Locale } from "@/lib/i18n";
export function PublicPage({
  locale,
  path,
  title,
  intro,
  children,
}: {
  locale: Locale;
  path: string;
  title: string;
  intro?: string;
  children: ReactNode;
}) {
  const dict = getDictionary(locale);
  return (
    <>
      <SiteHeader dict={dict} locale={locale} path={path} />
      <main
        id="main"
        className="bp-container public-page bp-stack bp-stack--lg"
      >
        <div className="public-page__heading">
          <p className="eyebrow">
            Beepclick /{" "}
            {locale === "fr"
              ? "Vos liens vont plus loin"
              : "Take your links further"}
          </p>
          <h1>{title}</h1>
          {intro && <p className="muted public-page__intro">{intro}</p>}
        </div>
        {children}
      </main>
      <SiteFooter dict={dict} locale={locale} />
    </>
  );
}
