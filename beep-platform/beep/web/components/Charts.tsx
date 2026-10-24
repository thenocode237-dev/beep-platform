import type { Breakdown, Point, Quota } from "@/lib/api";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";
import type { Locale } from "@/lib/i18n";

const intl = (locale: Locale) => (locale === "en" ? "en-GB" : "fr-FR");

export const fmtNumber = (locale: Locale, n: number) => new Intl.NumberFormat(intl(locale)).format(n);

function bucketLabel(locale: Locale, bucket: string, hourly: boolean) {
  const d = new Date(hourly ? bucket : `${bucket}T00:00:00Z`);
  return new Intl.DateTimeFormat(intl(locale), hourly ? { hour: "2-digit", minute: "2-digit", timeZone: "UTC" } : { day: "numeric", month: "short", timeZone: "UTC" }).format(d);
}

/**
 * Histogramme d'une seule série : une couleur (titre = nom de la série, pas de légende), barres fines arrondies,
 * 2 px d'écart, info-bulle native par barre, tableau équivalent pour les lecteurs d'écran.
 */
export function TrafficChart({ title, points, locale, unit, hourly = false }: { title: string; points: Point[]; locale: Locale; unit: string; hourly?: boolean }) {
  const max = Math.max(1, ...points.map((p) => p.clicks));
  const total = points.reduce((s, p) => s + p.clicks, 0);
  return (
    <figure className="traffic">
      <figcaption className="traffic__head">
        <span className="traffic__title">{title}</span>
        <span className="traffic__total bp-tabular">
          {fmtNumber(locale, total)} {unit}
        </span>
      </figcaption>
      <div className="traffic__plot" aria-hidden="true">
        <span className="traffic__max bp-tabular">{fmtNumber(locale, max)}</span>
        <div className="traffic__bars">
          {points.map((p) => (
            <span key={p.bucket} className="traffic__bar" title={`${bucketLabel(locale, p.bucket, hourly)} : ${fmtNumber(locale, p.clicks)} ${unit}`}>
              <span style={{ height: `${(p.clicks / max) * 100}%` }} />
            </span>
          ))}
        </div>
      </div>
      <div className="traffic__axis" aria-hidden="true">
        <span>{points[0] ? bucketLabel(locale, points[0].bucket, hourly) : ""}</span>
        <span>{points.length ? bucketLabel(locale, points[points.length - 1].bucket, hourly) : ""}</span>
      </div>
      <table className="bp-sr-only">
        <caption>{title}</caption>
        <tbody>
          {points.map((p) => (
            <tr key={p.bucket}>
              <th scope="row">{bucketLabel(locale, p.bucket, hourly)}</th>
              <td>{p.clicks}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/** Répartition : barres horizontales proportionnelles, valeur et part écrites (jamais la couleur seule). */
export function BreakdownList({ title, rows, t, locale }: { title: string; rows: Breakdown[]; t: ConsoleDict; locale: Locale }) {
  const total = rows.reduce((s, r) => s + r.clicks, 0);
  const label = (key: string) => t.stats.keys[key] ?? (key.length === 2 && key === key.toUpperCase() ? countryName(locale, key) : key);
  return (
    <section className="breakdown" aria-label={title}>
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="muted small">{t.stats.none}</p>
      ) : (
        <ul>
          {rows.map((r) => (
            <li key={r.key}>
              <span className="breakdown__label">{label(r.key)}</span>
              <span className="breakdown__value bp-tabular">
                {fmtNumber(locale, r.clicks)} · {Math.round((r.clicks / Math.max(1, total)) * 100)} %
              </span>
              <span className="breakdown__bar" aria-hidden="true">
                <span style={{ width: `${(r.clicks / Math.max(1, rows[0].clicks)) * 100}%` }} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function countryName(locale: Locale, code: string) {
  try {
    return new Intl.DisplayNames([intl(locale)], { type: "region" }).of(code) ?? code;
  } catch {
    return code;
  }
}

/** Jauge de quota : le niveau est écrit en toutes lettres en plus de la couleur. */
export function QuotaMeter({ q, t, locale }: { q: Quota; t: ConsoleDict; locale: Locale }) {
  const unlimited = q.limit === -1;
  const pct = unlimited ? 0 : q.limit === 0 ? 100 : Math.min(100, (q.used / q.limit) * 100);
  return (
    <div className={`quota quota--${q.level}`}>
      <div className="quota__head">
        <span>
          {t.overview.resources[q.resource]}
          {q.period === "monthly" ? ` (${t.overview.perMonth})` : ""}
        </span>
        <span className="bp-tabular">
          {fmtNumber(locale, q.used)} / {unlimited ? t.overview.unlimited : fmtNumber(locale, q.limit)}
        </span>
      </div>
      <div className="quota__track" role="meter" aria-label={t.overview.resources[q.resource]} aria-valuemin={0} aria-valuemax={unlimited ? q.used : q.limit} aria-valuenow={q.used}>
        <span style={{ width: `${pct}%` }} />
      </div>
      {q.level !== "ok" && <span className="quota__level">{t.overview.levels[q.level]}</span>}
    </div>
  );
}
