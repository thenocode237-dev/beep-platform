import { notFound } from "next/navigation";
import { getConsole, href, isLocale } from "@/lib/i18n";
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const t = getConsole(locale);
  const items = [
    { path: "domains", label: t.nav.domains },
    { path: "developer", label: "Conversions & API" },
    { path: "billing", label: t.nav.billing },
    { path: "settings", label: t.nav.settings },
  ];
  return (
    <div className="app-page bp-stack bp-stack--lg">
      <header className="app-heading">
        <h1>
          {locale === "en"
            ? "More from your workspace."
            : "Toutes les rubriques de votre espace."}
        </h1>
        <p>
          {locale === "en"
            ? "Manage your domains, integrations and account."
            : "Gérez vos domaines, vos connexions et votre compte."}
        </p>
      </header>
      {items.map((i) => (
        <a
          key={i.path}
          className="bp-card bp-btn bp-btn--secondary"
          href={href(locale, `/app/${i.path}`)}
        >
          {i.label}
        </a>
      ))}
    </div>
  );
}
