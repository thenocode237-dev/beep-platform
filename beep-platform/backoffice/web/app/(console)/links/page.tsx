import { Badge, Icon } from "@beep/ui";
import { SearchForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getLinks, getReservedSlugs } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

export default async function Links({ searchParams }: { searchParams: Promise<{ q?: string; workspaceId?: string; cursor?: string }> }) {
  const { q, workspaceId, cursor } = await searchParams;
  const [res, reserved] = await Promise.all([getLinks({ q, workspaceId, cursor }), getReservedSlugs()]);
  const more = (c: string) => `/links?${new URLSearchParams({ ...(q ? { q } : {}), ...(workspaceId ? { workspaceId } : {}), cursor: c })}`;

  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Chaque lien, vérifiable.</h1>
          <p>Cherchez par slug, destination ou adresse du propriétaire. Un lien désactivé affiche une page d'avertissement, sur toutes les instances, sans délai.</p>
        </div>
      </div>

      <SearchForm action="/links" q={q} label="Slug, destination ou e-mail">
        {workspaceId && <input type="hidden" name="workspaceId" value={workspaceId} />}
      </SearchForm>
      {workspaceId && (
        <p className="muted">
          Filtré sur un workspace. <a href="/links">Retirer le filtre</a>
        </p>
      )}

      {!res.data ? (
        <Unavailable status={res.status} what="les liens" />
      ) : res.data.items.length === 0 ? (
        <div className="bp-card empty">
          <span className="empty__icon">
            <Icon name="link" size={32} />
          </span>
          <h2>Aucun lien trouvé</h2>
          <p className="muted">Essayez une partie du slug ou du domaine de destination.</p>
        </div>
      ) : (
        <>
          <ul className="cards cards--always">
            {res.data.items.map((l) => (
              <li key={l.id} className="bp-card cards__item">
                <div className="link-row">
                  <a className="cards__email" href={`/links/${l.id}`}>
                    /{l.slug}
                  </a>
                  <Badge soon={l.status === "disabled"}>{l.status === "disabled" ? "Désactivé" : "Actif"}</Badge>
                </div>
                <span className="break muted">{l.destination}</span>
                <span className="muted small">
                  {l.ownerEmail ?? "propriétaire inconnu"} · <span className="bp-tabular">{fmtDateTime(l.createdAt)}</span>
                </span>
              </li>
            ))}
          </ul>
          {res.data.nextCursor && (
            <a className="bp-btn bp-btn--secondary" href={more(res.data.nextCursor)}>
              Page suivante
            </a>
          )}
        </>
      )}

      <section className="bp-card bp-stack" aria-labelledby="reserved">
        <h2 id="reserved">Slugs réservés</h2>
        <p className="muted">
          Chemins du site, routes techniques et noms protégés : personne ne peut les utiliser. La liste couvre toutes les routes du site déclarées dans Caddy (contrôle
          automatique en CI).
        </p>
        {reserved.data ? (
          <ul className="chips">
            {reserved.data.items.map((s) => (
              <li key={s}>
                <code>{s}</code>
              </li>
            ))}
          </ul>
        ) : (
          <Unavailable status={reserved.status} what="les slugs réservés" />
        )}
      </section>
    </div>
  );
}
