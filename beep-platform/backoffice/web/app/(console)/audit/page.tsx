import { Button, Icon, TextField } from "@beep/ui";
import { Unavailable } from "@/components/Unavailable";
import { getAudit } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

export default async function Audit({ searchParams }: { searchParams: Promise<{ before?: string; action?: string; actor?: string }> }) {
  const { before, action, actor } = await searchParams;
  const res = await getAudit({ before, action, actor });
  const older = (seq: number) => `/audit?${new URLSearchParams({ ...(action ? { action } : {}), ...(actor ? { actor } : {}), before: String(seq) })}`;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Le journal d'audit.</h1>
          <p>Toute action du staff, en ajout seul : chaque entrée engage l'empreinte de la précédente. La base refuse modification et suppression.</p>
        </div>
        {res.data && (
          <span className={`page-count ${res.data.intact ? "" : "page-count--danger"}`}>
            <Icon name={res.data.intact ? "shield" : "info"} size={18} />
            {res.data.intact ? `Chaîne intègre · ${res.data.total} entrées` : `Chaîne rompue à l'entrée n° ${res.data.brokenAt}`}
          </span>
        )}
      </div>

      <form className="search" action="/audit" method="get" role="search">
        <TextField name="action" label="Action (préfixe)" hint="Ex. : links. ou users.suspend" defaultValue={action} />
        <TextField name="actor" label="Auteur (e-mail)" defaultValue={actor} />
        <Button type="submit" variant="secondary">
          Filtrer
        </Button>
      </form>

      {!res.data ? (
        <Unavailable status={res.status} what="le journal d'audit" />
      ) : res.data.items.length === 0 ? (
        <div className="bp-card empty">
          <h2>Aucune entrée</h2>
          <p className="muted">Aucune action ne correspond à ces filtres.</p>
        </div>
      ) : (
        <>
          <ol className="audit">
            {res.data.items.map((e) => (
              <li key={e.seq} className="bp-card">
                <div className="link-row">
                  <code className="audit__action">{e.action}</code>
                  <span className="muted small bp-tabular">
                    n° {e.seq} · {fmtDateTime(e.at)}
                  </span>
                </div>
                <p className="break">
                  <strong>{e.actorEmail ?? "inconnu"}</strong>
                  {e.target && <> → {e.target}</>}
                </p>
                {e.detail && <p className="muted break small">{e.detail}</p>}
                <p className="muted small">IP {e.ip ?? "—"}</p>
              </li>
            ))}
          </ol>
          {res.data.nextBefore && (
            <a className="bp-btn bp-btn--secondary" href={older(res.data.nextBefore)}>
              Entrées plus anciennes
            </a>
          )}
        </>
      )}
    </div>
  );
}
