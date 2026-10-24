import { fmtDay, fmtNumber } from "@/lib/format";

/**
 * Histogramme d'une seule série (une couleur, pas de légende : le titre la nomme). Barres fines arrondies côté valeur,
 * 2 px d'écart, info-bulle native par barre, et tableau équivalent pour les lecteurs d'écran.
 */
export function BarChart({ title, points, unit }: { title: string; points: { day: string; count: number }[]; unit: string }) {
  const max = Math.max(1, ...points.map((p) => p.count));
  const total = points.reduce((s, p) => s + p.count, 0);
  const peak = points.reduce((best, p) => (p.count > best.count ? p : best), points[0] ?? { day: "", count: 0 });
  return (
    <figure className="chart">
      <figcaption className="chart__head">
        <span className="chart__title">{title}</span>
        <span className="chart__total bp-tabular">
          {fmtNumber(total)} {unit}
        </span>
      </figcaption>
      <div className="chart__plot" aria-hidden="true">
        <span className="chart__max bp-tabular">{fmtNumber(max)}</span>
        <div className="chart__bars">
          {points.map((p) => (
            <span key={p.day} className="chart__bar" title={`${fmtDay(p.day)} : ${fmtNumber(p.count)} ${unit}`}>
              <span style={{ height: `${(p.count / max) * 100}%` }} />
            </span>
          ))}
        </div>
      </div>
      <div className="chart__axis" aria-hidden="true">
        <span>{points[0] ? fmtDay(points[0].day) : ""}</span>
        {peak.count > 0 && <span>Pic : {fmtDay(peak.day)}</span>}
        <span>{points.length ? fmtDay(points[points.length - 1].day) : ""}</span>
      </div>
      <table className="bp-sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th scope="col">Jour</th>
            <th scope="col">{unit}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.day}>
              <td>{fmtDay(p.day)}</td>
              <td>{p.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/** Jauge d'usage face à un quota : la couleur de statut est doublée d'un libellé. */
export function UsageMeter({ label, used, limit, level }: { label: string; used: number; limit: number; level: "ok" | "warning" | "reached" }) {
  const unlimited = limit === -1;
  const pct = unlimited || limit === 0 ? (used > 0 && limit === 0 ? 100 : 0) : Math.min(100, (used / limit) * 100);
  const levelLabel = { ok: "Dans le quota", warning: "Bientôt atteint (≥ 80 %)", reached: "Quota atteint" }[level];
  return (
    <div className={`meter meter--${level}`}>
      <div className="meter__head">
        <span>{label}</span>
        <span className="bp-tabular">
          {fmtNumber(used)} / {unlimited ? "illimité" : fmtNumber(limit)}
        </span>
      </div>
      <div className="meter__track" role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={unlimited ? used : limit} aria-valuenow={used}>
        <span style={{ width: `${pct}%` }} />
      </div>
      <span className="meter__level">{levelLabel}</span>
    </div>
  );
}
