import { notFound } from "next/navigation";
import { Icon } from "@beep/ui";
import { StatsPanel } from "@/components/StatsPanel";
import { BioSettingsForm, BlocksEditor, CopyButton, DeleteButton } from "@/components/console";
import type { BioView, Stats } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function BioEditor({ params, searchParams }: { params: Promise<{ locale: string; id: string }>; searchParams: Promise<{ days?: string }> }) {
  const { locale, id } = await params;
  if (!isLocale(locale)) notFound();
  const days = Number((await searchParams).days ?? 30) || 30;
  const t = getConsole(locale);
  const [page, stats] = await Promise.all([coreGet<BioView>(`/bio-pages/${encodeURIComponent(id)}`), coreGet<Stats>(`/bio-pages/${encodeURIComponent(id)}/stats?days=${days}`)]);
  if (page.status === 404) notFound();
  const back = (
    <a className="text-link" href={href(locale, "/app/bio")}>
      <Icon name="arrow-left" size={16} /> {t.bio.back}
    </a>
  );
  if (!page.data)
    return (
      <div className="app-page bp-stack">
        {back}
        <div className="bp-card" role="alert">
          {t.common.unavailable}
        </div>
      </div>
    );
  const p = page.data;
  const self = href(locale, `/app/bio/${p.id}`);
  return (
    <div className="app-page bp-stack bp-stack--lg">
      {back}
      <div className="app-heading">
        <h1 className="break">{p.title}</h1>
        <div className="share">
          <a href={p.publicUrl} target="_blank" rel="noopener noreferrer" className="share__url">
            {p.publicUrl.replace(/^https?:\/\//, "")} <Icon name="external" size={16} />
          </a>
          <CopyButton text={p.publicUrl} t={t} />
        </div>
      </div>
      <div className="app-grid">
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="blocks">
          <h2 id="blocks">{t.bio.blocks}</h2>
          <p className="muted small">{t.bio.blocksSub}</p>
          <BlocksEditor t={t} page={p} />
        </section>
        <div className="bp-stack">
          <section className="bp-card bp-card--lg bp-stack" aria-labelledby="settings">
            <h2 id="settings">{t.bio.settings}</h2>
            <BioSettingsForm t={t} page={p} />
            <p className="muted small">{t.bio.badge}</p>
          </section>
          <section className="bp-card bp-stack" aria-labelledby="delete">
            <h2 id="delete">{t.bio.deleteTitle}</h2>
            <DeleteButton t={t} path={`/bio-pages/${p.id}`} redirectTo={href(locale, "/app/bio")} text={t.bio.deleteText} />
          </section>
        </div>
      </div>
      {stats.data && (
        <StatsPanel stats={stats.data} t={t} locale={locale} periodHref={(d) => `${self}?days=${d}`} primaryLabel={t.stats.views} secondaryLabel={t.stats.blockClicks} />
      )}
    </div>
  );
}
