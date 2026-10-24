import { Badge } from "@beep/ui";
import { Unavailable } from "@/components/Unavailable";
import { getPlans } from "@/lib/api";
import { entitlementLabels, fmtLimit, fmtNumber } from "@/lib/format";

export default async function Plans() {
  const res = await getPlans();
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Plans & droits.</h1>
          <p>
            Plans publiés et droits qu'ils accordent. Les overrides par workspace se règlent depuis la fiche d'un utilisateur. L'édition et le versionnement des
            plans arrivent avec la facturation (phase 2).
          </p>
        </div>
      </div>
      {!res.data ? (
        <Unavailable status={res.status} what="les plans" />
      ) : (
        <div className="plans">
          {res.data.items.map((p) => (
            <section key={p.code} className="bp-card bp-stack" aria-labelledby={`plan-${p.code}`}>
              <div className="link-row">
                <h2 id={`plan-${p.code}`}>{p.name}</h2>
                <Badge soon={!p.isPublic}>{p.isPublic ? "Commercialisé" : "Bientôt"}</Badge>
              </div>
              <p className="muted bp-tabular">
                {p.priceXaf === 0 ? "Gratuit" : `${fmtNumber(p.priceXaf)} FCFA / mois`} · version {p.version}
              </p>
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
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
