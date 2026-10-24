import { notFound } from "next/navigation";
import { Badge, Button, Icon, TextField } from "@beep/ui";
import { fmtNumber } from "@/components/Charts";
import { CopyButton, CreateLinkForm } from "@/components/console";
import type { LinksPage } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Links({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ q?: string; cursor?: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { q, cursor } = await searchParams;
  const t = getConsole(locale);
  const query = new URLSearchParams({ limit: "20", ...(q ? { q } : {}), ...(cursor ? { cursor } : {}) });
  const res = await coreGet<LinksPage>(`/links?${query}`);

  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{t.links.title}</h1>
        <p className="muted">{t.links.sub}</p>
      </div>
      <div className="app-grid app-grid--aside">
        <section className="bp-stack" aria-labelledby="list">
          <h2 id="list" className="bp-sr-only">
            {t.links.title}
          </h2>
          <form className="app-search" role="search" method="get" action={href(locale, "/app/links")}>
            <TextField name="q" type="search" label={t.links.search} defaultValue={q} />
            <Button type="submit" variant="secondary">
              <Icon name="search" size={18} />
              <span className="bp-sr-only">{t.links.search}</span>
            </Button>
          </form>
          {!res.data ? (
            <div className="bp-card" role="alert">
              {t.common.unavailable}
            </div>
          ) : res.data.items.length === 0 ? (
            <div className="bp-card empty-state">
              <Icon name="link" size={28} />
              <p>{q ? t.links.noMatch : t.links.empty}</p>
            </div>
          ) : (
            <ul className="link-cards">
              {res.data.items.map((l) => (
                <li key={l.id} className="bp-card">
                  <div className="link-card__top">
                    <a href={href(locale, `/app/links/${l.id}`)} className="link-list__slug">
                      {l.shortUrl.replace(/^https?:\/\//, "")}
                    </a>
                    <CopyButton text={l.shortUrl} t={t} compact />
                  </div>
                  {l.title && <strong className="link-card__title">{l.title}</strong>}
                  <span className="muted small link-list__dest">{l.destination}</span>
                  <div className="link-card__foot">
                    <span className="bp-tabular">
                      {fmtNumber(locale, l.clicks30d ?? 0)} <span className="muted small">{t.links.last30}</span>
                    </span>
                    {l.status === "disabled" && <Badge soon>{t.links.disabled}</Badge>}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {res.data?.nextCursor && (
            <a className="bp-btn bp-btn--secondary" href={`${href(locale, "/app/links")}?${new URLSearchParams({ ...(q ? { q } : {}), cursor: res.data.nextCursor })}`}>
              {t.links.more}
            </a>
          )}
        </section>
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="create">
          <h2 id="create">{t.links.create}</h2>
          <CreateLinkForm t={t} locale={locale} />
        </section>
      </div>
    </div>
  );
}
