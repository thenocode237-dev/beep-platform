import { notFound } from "next/navigation";
import { Icon } from "@beep/ui";
import { fmtNumber } from "@/components/Charts";
import { BioCreateForm, CopyButton } from "@/components/console";
import type { BioView } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function BioPages({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const t = getConsole(locale);
  const res = await coreGet<{ items: BioView[] }>("/bio-pages");
  const site = process.env.BEEP_PUBLIC_URL || "https://beep.click";

  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{t.bio.title}</h1>
        <p className="muted">{t.bio.sub}</p>
      </div>
      <div className="app-grid app-grid--aside">
        <section className="bp-stack" aria-labelledby="bio-list">
          <h2 id="bio-list" className="bp-sr-only">
            {t.bio.title}
          </h2>
          {!res.data ? (
            <div className="bp-card" role="alert">
              {t.common.unavailable}
            </div>
          ) : res.data.items.length === 0 ? (
            <div className="bp-card empty-state">
              <Icon name="bio" size={28} />
              <p>{t.bio.empty}</p>
            </div>
          ) : (
            <ul className="link-cards">
              {res.data.items.map((p) => (
                <li key={p.id} className="bp-card">
                  <div className="link-card__top">
                    <a href={href(locale, `/app/bio/${p.id}`)} className="link-list__slug">
                      {p.title}
                    </a>
                    <CopyButton text={p.publicUrl} t={t} compact />
                  </div>
                  <span className="muted small">{p.publicUrl.replace(/^https?:\/\//, "")}</span>
                  <span className="bp-tabular">
                    {fmtNumber(locale, p.views30d)} <span className="muted small">{t.bio.views30}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="bio-create">
          <h2 id="bio-create">{t.bio.create}</h2>
          <BioCreateForm t={t} locale={locale} site={site} />
        </section>
      </div>
    </div>
  );
}
