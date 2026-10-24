import { Badge } from "@beep/ui";
import { InviteForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getInvitations } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

const label = { pending: "En attente", used: "Utilisée", expired: "Expirée" } as const;

export default async function Invitations({ searchParams }: { searchParams: Promise<{ cursor?: string }> }) {
  const { cursor } = await searchParams;
  const res = await getInvitations(cursor);
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>La bêta, sur invitation.</h1>
          <p>
            Pendant la bêta privée, seules les adresses invitées peuvent créer un compte (mot de passe, lien magique ou Google/GitHub). L'invitation est valable 30
            jours ; la renvoyer prolonge sa validité.
          </p>
        </div>
      </div>
      <div className="detail-grid">
        <section className="bp-card bp-stack" aria-labelledby="invite">
          <h2 id="invite">Inviter une personne</h2>
          <InviteForm />
        </section>
        <section className="bp-card bp-stack" aria-labelledby="list">
          <h2 id="list">Invitations envoyées</h2>
          {!res.data ? (
            <Unavailable status={res.status} what="les invitations" />
          ) : res.data.items.length === 0 ? (
            <p className="muted">Aucune invitation pour le moment.</p>
          ) : (
            <ul className="history">
              {res.data.items.map((i) => (
                <li key={i.id}>
                  <div className="link-row">
                    <strong className="break">{i.email}</strong>
                    <Badge soon={i.status !== "pending"}>{label[i.status]}</Badge>
                  </div>
                  <span className="muted small bp-tabular">
                    Envoyée {fmtDateTime(i.createdAt)} · {i.usedAt ? `utilisée ${fmtDateTime(i.usedAt)}` : `expire ${fmtDateTime(i.expiresAt)}`}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {res.data?.nextCursor && (
            <a className="bp-btn bp-btn--secondary" href={`/invitations?cursor=${encodeURIComponent(res.data.nextCursor)}`}>
              Plus anciennes
            </a>
          )}
        </section>
      </div>
    </div>
  );
}
