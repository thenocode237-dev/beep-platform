import { Icon } from "@beep/ui";
import { BarChart } from "@/components/Charts";
import { Unavailable } from "@/components/Unavailable";
import { getMetrics } from "@/lib/api";
import { fmtBytes, fmtDateTime, fmtDuration, fmtNumber, fmtPercent } from "@/lib/format";

export default async function Metrics() {
  const res = await getMetrics(30);
  if (!res.data)
    return (
      <div className="bp-stack bp-stack--lg page">
        <h1>Tableau de bord</h1>
        <Unavailable status={res.status} what="les métriques" />
      </div>
    );
  const { core, backofficeBytes } = res.data;
  const lag = core.worker.lagSeconds;
  const workerLate = lag === null || lag > 300;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Le pouls de la plateforme.</h1>
          <p>Inscriptions, activation et trafic des 30 derniers jours (UTC). Mis à jour à {fmtDateTime(core.generatedAt)}.</p>
        </div>
      </div>

      <ul className="kpis">
        <li className="bp-card">
          <span className="kpi__label">Comptes</span>
          <strong className="kpi__value bp-tabular">{fmtNumber(core.users.total)}</strong>
          <span className="muted small">
            {fmtNumber(core.users.active)} actifs · {fmtNumber(core.users.pending)} en attente · {fmtNumber(core.users.suspended)} suspendus
          </span>
        </li>
        <li className="bp-card">
          <span className="kpi__label">Activation (premier lien en 10 min)</span>
          <strong className="kpi__value bp-tabular">{fmtPercent(core.activation.rate)}</strong>
          <span className="muted small">
            {fmtNumber(core.activation.activated)} sur {fmtNumber(core.activation.signups)} inscriptions
          </span>
        </li>
        <li className="bp-card">
          <span className="kpi__label">Ressources</span>
          <strong className="kpi__value bp-tabular">{fmtNumber(core.resources.links)}</strong>
          <span className="muted small">
            liens · {fmtNumber(core.resources.qrCodes)} QR · {fmtNumber(core.resources.bioPages)} pages bio
          </span>
        </li>
        <li className={`bp-card ${workerLate ? "kpi--warning" : ""}`}>
          <span className="kpi__label">Retard du worker</span>
          <strong className="kpi__value bp-tabular">{fmtDuration(lag)}</strong>
          <span className="small">
            <Icon name={workerLate ? "info" : "check"} size={15} /> {workerLate ? "Agrégats en retard : vérifiez la vue Jobs" : "Agrégats à jour"}
          </span>
        </li>
      </ul>

      <div className="detail-grid">
        <section className="bp-card">
          <BarChart title="Inscriptions par jour" points={core.signups} unit="inscriptions" />
        </section>
        <section className="bp-card">
          <BarChart title="Clics et scans humains par jour" points={core.clicks} unit="clics" />
        </section>
      </div>

      <section className="bp-card bp-stack" aria-labelledby="infra">
        <h2 id="infra">Infrastructure</h2>
        <dl className="kv kv--wide">
          <dt>Base « beep »</dt>
          <dd className="bp-tabular">{fmtBytes(core.database.beepBytes)}</dd>
          <dt>Base « backoffice »</dt>
          <dd className="bp-tabular">{fmtBytes(backofficeBytes)}</dd>
          <dt>Dernier agrégat</dt>
          <dd className="bp-tabular">{fmtDateTime(core.worker.lastRollupAt)}</dd>
          <dt>File d'événements (instance qui répond)</dt>
          <dd className="bp-tabular">
            {fmtNumber(core.events.queued)} en attente · {fmtNumber(core.events.written)} écrits · {fmtNumber(core.events.dropped)} abandonnés
          </dd>
        </dl>
        {core.events.dropped > 0 && <p className="warning-text">Des événements ont été abandonnés (file pleine ou base indisponible) : les redirections, elles, ont continué.</p>}
      </section>
    </div>
  );
}
