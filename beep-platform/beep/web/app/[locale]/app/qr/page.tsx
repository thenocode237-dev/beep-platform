import { notFound } from "next/navigation";
import { Badge, Icon } from "@beep/ui";
import { fmtNumber } from "@/components/Charts";
import { QrCreateForm, QrDeleteButton } from "@/components/console";
import type { LinksPage, QrView } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function QrCodes({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ link?: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { link } = await searchParams;
  const t = getConsole(locale);
  const [qrs, links] = await Promise.all([coreGet<{ items: QrView[] }>("/qr-codes?limit=100"), coreGet<LinksPage>("/links?limit=100")]);
  const workspace=await coreGet<{entitlements:Record<string,number>}>("/workspace");
  const linkById = new Map((links.data?.items ?? []).map((l) => [l.id, l]));

  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{t.qr.title}</h1>
        <p className="muted">{t.qr.sub}</p>
      </div>
      <div className="app-grid app-grid--aside">
        <section className="bp-stack" aria-labelledby="qr-list">
          <h2 id="qr-list" className="bp-sr-only">
            {t.qr.title}
          </h2>
          {!qrs.data ? (
            <div className="bp-card" role="alert">
              {t.common.unavailable}
            </div>
          ) : qrs.data.items.length === 0 ? (
            <div className="bp-card empty-state">
              <Icon name="qr" size={28} />
              <p>{t.qr.empty}</p>
            </div>
          ) : (
            <ul className="qr-grid">
              {qrs.data.items.map((q) => {
                const target = q.linkId ? linkById.get(q.linkId) : undefined;
                return (
                  <li key={q.id} className="bp-card qr-card">
                    {/* aperçu servi par l'API (même hôte, cookie de session) ; régénéré après modification */}
                    <img className="qr-card__img" src={`/v1/qr-codes/${q.id}/render?format=svg&size=480&v=${encodeURIComponent(q.updatedAt)}`} alt={t.qr.preview} width={160} height={160} />
                    <div className="qr-card__body">
                      <div className="link-card__top">
                        <strong>{q.title ?? (q.kind === "dynamic" ? `/q/${q.code}` : t.qr.types[(q.contentType ?? "url") as keyof typeof t.qr.types])}</strong>
                        <Badge soon={q.kind === "static"}>{t.qr.kindShort[q.kind]}</Badge>
                      </div>
                      {q.kind === "dynamic" ? (
                        <span className="muted small break">
                          {t.qr.follows} {target ? `/${target.slug}` : "—"} · {fmtNumber(locale, q.scans30d)} {t.qr.scans30}
                        </span>
                      ) : (
                        <span className="muted small break">{q.encoded.length > 80 ? `${q.encoded.slice(0, 80)}…` : q.encoded}</span>
                      )}
                      <div className="qr-card__actions">
                        <span className="small">{t.qr.download} :</span>
                        {(["svg", "png", "pdf"] as const).map((f) => (
                          <a key={f} className="bp-btn bp-btn--secondary bp-btn--sm" href={`/v1/qr-codes/${q.id}/render?format=${f}&size=2048&download=1`} download>
                            {f.toUpperCase()}
                          </a>
                        ))}
                        <QrDeleteButton t={t} qr={q} />
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="qr-create">
          <h2 id="qr-create">{t.qr.create}</h2>
          <QrCreateForm t={t} locale={locale} links={links.data?.items ?? []} presetLink={link} advancedAllowed={workspace.data?.entitlements["features.qr_advanced"]===1} />
        </section>
      </div>
    </div>
  );
}
