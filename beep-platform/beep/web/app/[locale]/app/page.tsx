import { notFound } from "next/navigation";
import { Badge, Icon } from "@beep/ui";
import { QuotaMeter, TrafficChart, fmtNumber } from "@/components/Charts";
import { CopyButton, QuickLinkForm } from "@/components/console";
import type { LinksPage, WorkspaceView } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Overview({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const t = getConsole(locale);
  const [ws, links] = await Promise.all([coreGet<WorkspaceView>("/workspace"), coreGet<LinksPage>("/links?limit=5")]);

  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{t.overview.title}</h1>
        <p className="muted">{t.overview.sub}</p>
      </div>
      <section className="bp-card bp-card--lg">
        <QuickLinkForm t={t} />
      </section>

      {!ws.data ? (
        <div className="bp-card" role="alert">
          {t.common.unavailable}
        </div>
      ) : (
        <div className="app-grid">
          <section className="bp-card bp-stack" aria-labelledby="traffic">
            <h2 id="traffic" className="bp-sr-only">
              {t.overview.traffic}
            </h2>
            <TrafficChart title={t.overview.traffic} points={ws.data.daily} locale={locale} unit={t.common.clicks} />
          </section>
          <section className="bp-card bp-stack" aria-labelledby="usage">
            <div className="section-head">
              <h2 id="usage">{t.overview.usage}</h2>
              <Badge>
                {t.overview.plan} {ws.data.plan.name}
              </Badge>
            </div>
            {ws.data.quotas.map((q) => (
              <QuotaMeter key={q.resource} q={q} t={t} locale={locale} />
            ))}
            <p className="muted small">{t.overview.usageNote}</p>
          </section>
        </div>
      )}

      <section className="bp-card bp-stack" aria-labelledby="recent">
        <div className="section-head">
          <h2 id="recent">{t.overview.recent}</h2>
          <a className="text-link" href={href(locale, "/app/links")}>
            {t.overview.seeAll} <Icon name="arrow-right" size={16} />
          </a>
        </div>
        {links.data && links.data.items.length > 0 ? (
          <ul className="link-list">
            {links.data.items.map((l) => (
              <li key={l.id}>
                <div className="link-list__main">
                  <a href={href(locale, `/app/links/${l.id}`)} className="link-list__slug">
                    {l.shortUrl.replace(/^https?:\/\//, "")}
                  </a>
                  <span className="muted small link-list__dest">{l.title ?? l.destination}</span>
                </div>
                <span className="link-list__clicks bp-tabular">
                  {fmtNumber(locale, l.clicks30d ?? 0)} <span className="muted small">{t.links.last30}</span>
                </span>
                <CopyButton text={l.shortUrl} t={t} compact />
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">{t.overview.empty}</p>
        )}
      </section>
    </div>
  );
}
