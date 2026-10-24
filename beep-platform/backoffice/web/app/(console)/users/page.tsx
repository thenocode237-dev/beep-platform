import { Badge, Icon } from "@beep/ui";
import { SearchForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getUsers } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

const statusLabel: Record<string, string> = { active: "Actif", pending: "En attente", suspended: "Suspendu" };

export default async function Users({ searchParams }: { searchParams: Promise<{ cursor?: string; q?: string }> }) {
  const { cursor, q } = await searchParams;
  const res = await getUsers(cursor, q);

  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Les personnes derrière les liens.</h1>
          <p>Retrouvez un compte, son statut et la protection de son accès. Chaque consultation est journalisée.</p>
        </div>
        {res.data && (
          <span className="page-count">
            <Icon name="users" size={18} />
            {res.data.items.length} sur cette page
          </span>
        )}
      </div>

      <SearchForm action="/users" q={q} label="Rechercher par adresse e-mail" />

      {!res.data ? (
        <Unavailable status={res.status} what="la liste des utilisateurs" />
      ) : res.data.items.length === 0 ? (
        <div className="bp-card empty">
          <span className="empty__icon">
            <Icon name="users" size={32} />
          </span>
          <h2>{q ? "Aucun compte ne correspond" : "Aucun utilisateur pour le moment"}</h2>
          <p className="muted">{q ? "Vérifiez l'orthographe ou cherchez une partie de l'adresse." : "Les inscriptions faites sur le site apparaîtront ici."}</p>
        </div>
      ) : (
        <>
          {/* Mobile : cartes ; ≥ 768 px : tableau. Même donnée, deux présentations, pas de défilement horizontal. */}
          <ul className="cards">
            {res.data.items.map((u) => (
              <li key={u.id} className="bp-card cards__item">
                <a className="cards__email" href={`/users/${u.id}`}>
                  {u.email}
                </a>
                <div className="bp-row">
                  <Badge soon={u.status !== "active"}>{statusLabel[u.status] ?? u.status}</Badge>
                  <Badge soon={!u.emailVerified}>{u.emailVerified ? "E-mail vérifié" : "E-mail non vérifié"}</Badge>
                  <Badge soon={!u.totpEnabled}>{u.totpEnabled ? "TOTP actif" : "Sans TOTP"}</Badge>
                </div>
                <span className="muted bp-tabular">{fmtDateTime(u.createdAt)}</span>
              </li>
            ))}
          </ul>
          <div className="bp-card table-wrap">
            <table className="table">
              <caption className="bp-sr-only">Liste des utilisateurs</caption>
              <thead>
                <tr>
                  <th scope="col">E-mail</th>
                  <th scope="col">Statut</th>
                  <th scope="col">E-mail vérifié</th>
                  <th scope="col">TOTP</th>
                  <th scope="col">Inscription</th>
                </tr>
              </thead>
              <tbody>
                {res.data.items.map((u) => (
                  <tr key={u.id}>
                    <th scope="row">
                      <a href={`/users/${u.id}`}>{u.email}</a>
                    </th>
                    <td>
                      <span className={`table-status table-status--${u.status}`}>{statusLabel[u.status] ?? u.status}</span>
                    </td>
                    <td>{u.emailVerified ? "Oui" : "Non"}</td>
                    <td>{u.totpEnabled ? "Oui" : "Non"}</td>
                    <td className="bp-tabular">{fmtDateTime(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {res.data.nextCursor && (
            <a className="bp-btn bp-btn--secondary" href={`/users?cursor=${encodeURIComponent(res.data.nextCursor)}${q ? `&q=${encodeURIComponent(q)}` : ""}`}>
              Page suivante
            </a>
          )}
        </>
      )}
    </div>
  );
}
