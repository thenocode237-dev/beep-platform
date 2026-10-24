import { Icon } from "@beep/ui";
import type { Stats } from "@/lib/api";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";
import type { Locale } from "@/lib/i18n";
import { BreakdownList, TrafficChart, fmtNumber } from "./Charts";

/** Statistiques d'une ressource : période, totaux, évolution, répartitions, export CSV. */
export function StatsPanel({
  stats,
  t,
  locale,
  periodHref,
  csvHref,
  primaryLabel,
  secondaryLabel,
}: {
  stats: Stats;
  t: ConsoleDict;
  locale: Locale;
  periodHref: (days: number) => string;
  csvHref?: string;
  primaryLabel: string;
  secondaryLabel?: string;
}) {
  const periods = [1, 7, 30, 90].filter((d) => d <= stats.maxDays || d === 1);
  return (
    <section className="bp-card bp-card--lg bp-stack" aria-labelledby="stats-title">
      <div className="section-head">
        <h2 id="stats-title">{t.stats.title}</h2>
        <nav className="periods" aria-label={t.stats.title}>
          {periods.map((d) => (
            <a key={d} href={periodHref(d)} aria-current={stats.days === d ? "page" : undefined}>
              {t.stats.periods[String(d) as keyof ConsoleDict["stats"]["periods"]]}
            </a>
          ))}
        </nav>
      </div>
      <ul className="totals">
        <li>
          <span>{primaryLabel}</span>
          <strong className="bp-tabular">{fmtNumber(locale, stats.clicks)}</strong>
        </li>
        {stats.secondaryClicks !== null && secondaryLabel && (
          <li>
            <span>{secondaryLabel}</span>
            <strong className="bp-tabular">{fmtNumber(locale, stats.secondaryClicks)}</strong>
          </li>
        )}
        <li>
          <span title={t.stats.uniquesNote}>{t.stats.uniques}</span>
          <strong className="bp-tabular">{fmtNumber(locale, stats.uniques)}</strong>
        </li>
      </ul>
      <TrafficChart
        title={`${t.stats.series} · ${stats.granularity === "hour" ? t.stats.hourly : t.stats.daily}`}
        points={stats.series}
        locale={locale}
        unit={primaryLabel.toLowerCase()}
        hourly={stats.granularity === "hour"}
      />
      <div className="breakdowns">
        {Object.entries(stats.breakdowns).map(([dim, rows]) => (
          <BreakdownList key={dim} title={t.stats.dimensions[dim as keyof ConsoleDict["stats"]["dimensions"]] ?? dim} rows={rows} t={t} locale={locale} />
        ))}
      </div>
      {!stats.advanced && <p className="muted small">{t.stats.advancedLocked}</p>}
      <div className="stats-foot">
        <p className="muted small">
          {t.stats.delay} {stats.maxDays < 395 && t.stats.retention.replace("{days}", String(stats.maxDays))}
        </p>
        {csvHref &&
          (stats.exportAllowed ? (
            <a className="bp-btn bp-btn--secondary bp-btn--sm" href={csvHref} download>
              <Icon name="download" size={18} /> {t.stats.export}
            </a>
          ) : (
            <p className="muted small">{t.stats.exportLocked}</p>
          ))}
      </div>
    </section>
  );
}
