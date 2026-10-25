import { Badge } from "@beep/ui";
import { PlanDraftEditor } from "@/components/BillingForms";
import { Unavailable } from "@/components/Unavailable";
import { getMe, getPlanDrafts, getPlans } from "@/lib/api";
import { entitlementLabels, fmtDateTime, fmtLimit, fmtNumber } from "@/lib/format";

export default async function Plans({ searchParams }: { searchParams: Promise<{ edit?: string }> }) {
  const { edit } = await searchParams;
  const [res, drafts, me] = await Promise.all([getPlans(), getPlanDrafts(), getMe()]);
  const can = (p: string) => me.data?.permissions.includes(p) ?? false;
  const draftOf = (code: string) => drafts.data?.items.find((d) => d.code === code) ?? null;
  const editing = edit === "new" ? "new" : edit;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Plans & droits.</h1>
          <p>
            Plans publiés et droits qu'ils accordent. Un plan se modifie par brouillon puis se publie vers le core sans déploiement : la version est incrémentée, les
            overrides par workspace restent prioritaires (fiche utilisateur).
          </p>
        </div>
        {can("PLANS_EDIT") && (
          <a className="bp-btn bp-btn--secondary" href="/plans?edit=new">
            Nouveau plan
          </a>
        )}
      </div>
      {!res.data ? (
        <Unavailable status={res.status} what="les plans" />
      ) : (
        <div className="plans">
          {res.data.items.map((p) => {
            const d = draftOf(p.code);
            return (
              <section key={p.code} className="bp-card bp-stack" aria-labelledby={`plan-${p.code}`}>
                <div className="link-row">
                  <h2 id={`plan-${p.code}`}>{p.name}</h2>
                  <Badge soon={!p.isPublic}>{p.isPublic ? "Commercialisé" : "Bientôt"}</Badge>
                </div>
                <p className="muted bp-tabular">
                  {p.priceXaf === 0 ? "Gratuit" : `${fmtNumber(p.priceXaf)} FCFA / mois · ${fmtNumber(p.priceQuarterlyXaf)} / trimestre · ${fmtNumber(p.priceAnnualXaf)} / an`} ·
                  version {p.version}
                </p>
                {d && (
                  <p className="small">
                    <Badge soon>Brouillon</Badge> modifié {fmtDateTime(d.updatedAt)} par {d.updatedBy}
                  </p>
                )}
                <ul className="rights">
                  {Object.keys(entitlementLabels)
                    .filter((k) => k in p.entitlements)
                    .map((k) => (
                      <li key={k}>
                        <span>{entitlementLabels[k]}</span>
                        <strong className="bp-tabular">{k.startsWith("features.") ? (p.entitlements[k] > 0 ? "Oui" : "Non") : fmtLimit(p.entitlements[k])}</strong>
                      </li>
                    ))}
                </ul>
                {can("PLANS_EDIT") && (
                  <a className="bp-btn bp-btn--secondary" href={`/plans?edit=${p.code}`}>
                    {d ? "Reprendre le brouillon" : "Modifier"}
                  </a>
                )}
              </section>
            );
          })}
        </div>
      )}
      {can("PLANS_EDIT") && editing && res.data && (
        <section className="bp-card bp-stack" aria-labelledby="editor">
          <h2 id="editor">{editing === "new" ? "Nouveau plan" : `Brouillon du plan ${editing}`}</h2>
          <PlanDraftEditor
            plan={editing === "new" ? null : (res.data.items.find((p) => p.code === editing) ?? null)}
            draft={editing === "new" ? null : draftOf(editing)}
            canPublish={can("PLANS_PUBLISH")}
          />
        </section>
      )}
    </div>
  );
}
