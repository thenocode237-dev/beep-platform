import { notFound } from "next/navigation";
import { Alert, Icon } from "@beep/ui";
import { LinkRulesPanel } from "@/components/LinkRulesPanel";
import { StatsPanel } from "@/components/StatsPanel";
import { CopyButton, DeleteButton, EditLinkForm } from "@/components/console";
import type { LinkView, Stats } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function LinkDetail({ params, searchParams }: { params: Promise<{ locale: string; id: string }>; searchParams: Promise<{ days?: string }> }) {
  const { locale, id } = await params;
  if (!isLocale(locale)) notFound();
  const days = Number((await searchParams).days ?? 30) || 30;
  const t = getConsole(locale);
  const [link, stats] = await Promise.all([coreGet<LinkView>(`/links/${encodeURIComponent(id)}`), coreGet<Stats>(`/links/${encodeURIComponent(id)}/stats?days=${days}`)]);
  if (link.status === 404) notFound();
  const back = (
    <a className="text-link" href={href(locale, "/app/links")}>
      <Icon name="arrow-left" size={16} /> {t.links.back}
    </a>
  );
  if (!link.data)
    return (
      <div className="app-page bp-stack">
        {back}
        <div className="bp-card" role="alert">
          {t.common.unavailable}
        </div>
      </div>
    );
  const workspace=await coreGet<{entitlements:Record<string,number>}>("/workspace");
  const l = link.data;
  const self = href(locale, `/app/links/${l.id}`);
  return (
    <div className="app-page bp-stack bp-stack--lg">
      {back}
      <div className="app-heading">
        <h1 className="break">{l.title ?? l.shortUrl.replace(/^https?:\/\//, "")}</h1>
        <div className="share">
          <a href={l.shortUrl} target="_blank" rel="noopener noreferrer" className="share__url">
            {l.shortUrl.replace(/^https?:\/\//, "")}
          </a>
          <CopyButton text={l.shortUrl} t={t} />
        </div>
        <p className="muted small break">→ {l.destination}</p>
      </div>
      {l.status === "disabled" && <Alert tone="danger">{t.links.disabledText}</Alert>}

      {stats.data ? (
        <StatsPanel
          stats={stats.data}
          t={t}
          locale={locale}
          periodHref={(d) => `${self}?days=${d}`}
          csvHref={`/v1/links/${l.id}/stats.csv?days=${stats.data.days}`}
          primaryLabel={t.stats.clicks}
        />
      ) : (
        <div className="bp-card" role="alert">
          {t.common.unavailable}
        </div>
      )}

      <LinkRulesPanel link={l} locale={locale} entitlements={workspace.data?.entitlements??{}}/>
      <div className="app-grid">
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="settings">
          <h2 id="settings">{t.links.settings}</h2>
          <EditLinkForm t={t} link={l} />
        </section>
        <div className="bp-stack">
          <section className="bp-card bp-stack" aria-labelledby="qr">
            <h2 id="qr">{t.nav.qr}</h2>
            <a className="bp-btn bp-btn--secondary" href={`${href(locale, "/app/qr")}?link=${l.id}`}>
              <Icon name="qr" size={18} /> {t.links.createQr}
            </a>
          </section>
          <section className="bp-card bp-stack" aria-labelledby="delete">
            <h2 id="delete">{t.links.deleteTitle}</h2>
            <DeleteButton t={t} path={`/links/${l.id}`} redirectTo={href(locale, "/app/links")} text={t.links.deleteText} />
          </section>
        </div>
      </div>
    </div>
  );
}
