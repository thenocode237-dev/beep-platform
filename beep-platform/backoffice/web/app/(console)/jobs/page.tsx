import { Icon } from "@beep/ui";
import { Unavailable } from "@/components/Unavailable";
import { getJobs } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

const every = (iso: string) => ({ PT1M: "chaque minute", PT15M: "toutes les 15 min", PT1H: "toutes les heures", PT6H: "toutes les 6 h" })[iso] ?? iso;

export default async function Jobs() {
  const res = await getJobs();
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Les tâches de fond.</h1>
          <p>Agrégats, partitions, rétention et purges du worker : dernière exécution, issue et volume des 24 dernières heures.</p>
        </div>
      </div>
      {!res.data ? (
        <Unavailable status={res.status} what="l'état des jobs" />
      ) : (
        <ul className="cards cards--always">
          {res.data.items.map((j) => {
            const state = j.lastOk === null ? "never" : j.lastOk ? "ok" : "failed";
            return (
              <li key={j.name} className="bp-card cards__item">
                <div className="link-row">
                  <strong>
                    <code>{j.name}</code>
                  </strong>
                  <span className={`job-state job-state--${state}`}>
                    <Icon name={state === "ok" ? "check" : state === "failed" ? "ban" : "clock"} size={15} />
                    {state === "ok" ? "Réussi" : state === "failed" ? "En échec" : "Jamais exécuté"}
                  </span>
                </div>
                <span className="muted">
                  {j.description} — {every(j.schedule)}
                </span>
                <dl className="kv">
                  <dt>Dernière exécution</dt>
                  <dd className="bp-tabular">{fmtDateTime(j.lastStartedAt)}</dd>
                  <dt>Dernier succès</dt>
                  <dd className="bp-tabular">{fmtDateTime(j.lastSuccessAt)}</dd>
                  <dt>24 h</dt>
                  <dd className="bp-tabular">
                    {j.runs24h} exécution(s), {j.failures24h} échec(s)
                  </dd>
                  {j.lastDetail && (
                    <>
                      <dt>Détail</dt>
                      <dd className="break">
                        <code>{j.lastDetail}</code>
                      </dd>
                    </>
                  )}
                </dl>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
