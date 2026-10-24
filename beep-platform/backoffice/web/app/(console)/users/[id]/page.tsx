import { Badge, Icon } from "@beep/ui";
import { UsageMeter } from "@/components/Charts";
import { AccountStatusForm, OverrideForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getMe, getUser } from "@/lib/api";
import { entitlementLabels, fmtDateTime, fmtLimit, resourceLabels } from "@/lib/format";

const statusLabel: Record<string, string> = { active: "Actif", pending: "En attente de confirmation", suspended: "Suspendu" };

export default async function UserDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [res, me] = await Promise.all([getUser(id), getMe()]);
  const can = (p: string) => me.data?.permissions.includes(p) ?? false;
  if (!res.data) {
    return (
      <div className="bp-stack bp-stack--lg page">
        <a className="back-link" href="/users">
          <Icon name="arrow-left" size={17} /> Utilisateurs
        </a>
        <Unavailable status={res.status} what="cette fiche" />
      </div>
    );
  }
  const { user, workspace, usage, entitlements, overrides } = res.data;
  const overridden = new Set(overrides.map((o) => o.key));

  return (
    <div className="bp-stack bp-stack--lg page">
      <a className="back-link" href="/users">
        <Icon name="arrow-left" size={17} /> Utilisateurs
      </a>
      <div className="page-heading">
        <div>
          <h1 className="break">{user.email}</h1>
          <div className="bp-row heading-badges">
            <Badge soon={user.status !== "active"}>{statusLabel[user.status] ?? user.status}</Badge>
            <Badge soon={!user.totpEnabled}>{user.totpEnabled ? "TOTP actif" : "Sans TOTP"}</Badge>
            {workspace && <Badge>Plan {workspace.plan}</Badge>}
          </div>
        </div>
      </div>

      <div className="detail-grid">
        <section className="bp-card bp-stack" aria-labelledby="account">
          <h2 id="account">Compte</h2>
          <dl className="kv">
            <dt>Inscription</dt>
            <dd className="bp-tabular">{fmtDateTime(user.createdAt)}</dd>
            <dt>Adresse vérifiée</dt>
            <dd>{user.emailVerified ? "Oui" : "Non"}</dd>
            <dt>Workspace</dt>
            <dd>{workspace ? `${workspace.name} (${workspace.status === "suspended" ? "suspendu" : "actif"})` : "Pas encore créé (aucune connexion depuis la phase 1)"}</dd>
            <dt>Premier lien</dt>
            <dd className="bp-tabular">{workspace?.firstLinkAt ? fmtDateTime(workspace.firstLinkAt) : "—"}</dd>
          </dl>
          {workspace && can("LINKS_READ") && (
            <a className="text-link" href={`/links?workspaceId=${workspace.id}`}>
              Voir ses liens <Icon name="arrow-right" size={16} />
            </a>
          )}
        </section>

        {workspace && (
          <section className="bp-card bp-stack" aria-labelledby="usage">
            <h2 id="usage">Usage face aux quotas</h2>
            {usage.map((u) => (
              <UsageMeter
                key={u.resource}
                label={`${resourceLabels[u.resource] ?? u.resource}${u.period === "monthly" ? " (ce mois-ci)" : ""}`}
                used={u.used}
                limit={u.limit}
                level={u.level}
              />
            ))}
          </section>
        )}
      </div>

      {workspace && (
        <section className="bp-card bp-stack" aria-labelledby="rights">
          <h2 id="rights">Droits effectifs</h2>
          <p className="muted">Valeurs du plan {workspace.plan}, remplacées clé par clé par les overrides du staff.</p>
          <ul className="rights">
            {Object.entries(entitlements).map(([k, v]) => (
              <li key={k}>
                <span>{entitlementLabels[k] ?? k}</span>
                <strong className="bp-tabular">
                  {k.startsWith("features.") ? (v > 0 ? "Oui" : "Non") : fmtLimit(v)}
                  {overridden.has(k) && <span className="override-tag"> override</span>}
                </strong>
              </li>
            ))}
          </ul>
          {overrides.length > 0 && (
            <>
              <h3>Overrides en place</h3>
              <ul className="history">
                {overrides.map((o) => (
                  <li key={o.key}>
                    <strong>{entitlementLabels[o.key] ?? o.key}</strong> = {fmtLimit(o.value)} — {o.reason}
                    <span className="muted bp-tabular"> · {fmtDateTime(o.updatedAt)}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}

      <div className="detail-grid">
        {workspace && can("ENTITLEMENTS_WRITE") && (
          <section className="bp-card bp-stack" aria-labelledby="override">
            <h2 id="override">Ajuster un droit</h2>
            <OverrideForm workspaceId={workspace.id} current={entitlements} />
          </section>
        )}
        {can("USERS_SUSPEND") && (
          <section className="bp-card bp-stack" aria-labelledby="status">
            <h2 id="status">{user.status === "suspended" ? "Réactiver le compte" : "Suspendre le compte"}</h2>
            <AccountStatusForm userId={user.id} suspended={user.status === "suspended"} />
          </section>
        )}
      </div>
    </div>
  );
}
