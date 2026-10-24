import { Badge, Icon } from "@beep/ui";
import { getUsers } from "@/lib/api";

const date = new Intl.DateTimeFormat("fr-FR", {
  dateStyle: "medium",
  timeStyle: "short",
});

export default async function Users({
  searchParams,
}: {
  searchParams: Promise<{ cursor?: string }>;
}) {
  const { cursor } = await searchParams;
  const res = await getUsers(cursor);

  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Les personnes derrière les liens.</h1>
          <p>
            Retrouvez les comptes Beep et vérifiez leur statut ainsi que la
            protection de leur accès.
          </p>
        </div>
        {res.data && (
          <span className="page-count">
            <Icon name="users" size={18} />
            {res.data.items.length} sur cette page
          </span>
        )}
      </div>

      {!res.data ? (
        <div className="bp-card" role="alert">
          {res.status === 0 || res.status >= 500
            ? "La liste est indisponible (beep-core injoignable). Réessayez dans un instant."
            : "Impossible de charger les utilisateurs."}
        </div>
      ) : res.data.items.length === 0 ? (
        <div className="bp-card empty">
          <span className="empty__icon">
            <Icon name="users" size={32} />
          </span>
          <h2>Aucun utilisateur pour le moment</h2>
          <p className="muted">
            Les inscriptions faites sur le site apparaîtront ici.
          </p>
        </div>
      ) : (
        <>
          {/* Mobile : cartes ; ≥ 768 px : tableau. Même donnée, deux présentations, pas de défilement horizontal. */}
          <ul className="cards">
            {res.data.items.map((u) => (
              <li key={u.id} className="bp-card cards__item">
                <strong className="cards__email">{u.email}</strong>
                <div className="bp-row">
                  <Badge>{u.status}</Badge>
                  <Badge soon={!u.emailVerified}>
                    {u.emailVerified ? "E-mail vérifié" : "E-mail non vérifié"}
                  </Badge>
                  <Badge soon={!u.totpEnabled}>
                    {u.totpEnabled ? "TOTP actif" : "Sans TOTP"}
                  </Badge>
                </div>
                <span className="muted bp-tabular">
                  {date.format(new Date(u.createdAt))}
                </span>
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
                    <th scope="row">{u.email}</th>
                    <td>
                      <span className="table-status">{u.status}</span>
                    </td>
                    <td>{u.emailVerified ? "Oui" : "Non"}</td>
                    <td>{u.totpEnabled ? "Oui" : "Non"}</td>
                    <td className="bp-tabular">
                      {date.format(new Date(u.createdAt))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {res.data.nextCursor && (
            <a
              className="bp-btn bp-btn--secondary"
              href={`/users?cursor=${encodeURIComponent(res.data.nextCursor)}`}
            >
              Page suivante
            </a>
          )}
        </>
      )}
    </div>
  );
}
