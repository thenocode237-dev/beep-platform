import { Badge } from "@beep/ui";
import { CouponForm, CouponToggle } from "@/components/BillingForms";
import { Unavailable } from "@/components/Unavailable";
import { getCoupons, getMe, getPlans } from "@/lib/api";
import { fmtDateTime, fmtXaf } from "@/lib/format";

export default async function Coupons() {
  const [res, plans, me] = await Promise.all([getCoupons(), getPlans(), getMe()]);
  const canWrite = me.data?.permissions.includes("BILLING_WRITE") ?? false;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Coupons.</h1>
          <p>Remise en pourcentage ou en montant fixe, limitée en utilisations, en durée et par plan. Un coupon est consommé au paiement, pas à la saisie.</p>
        </div>
      </div>
      <div className="detail-grid">
        {canWrite && plans.data && (
          <section className="bp-card bp-stack" aria-labelledby="new">
            <h2 id="new">Nouveau coupon</h2>
            <CouponForm plans={plans.data.items} />
          </section>
        )}
        <section className="bp-card bp-stack" aria-labelledby="list">
          <h2 id="list">Coupons existants</h2>
          {!res.data ? (
            <Unavailable status={res.status} what="les coupons" />
          ) : res.data.items.length === 0 ? (
            <p className="muted">Aucun coupon.</p>
          ) : (
            <ul className="history">
              {res.data.items.map((c) => (
                <li key={c.code}>
                  <div className="link-row">
                    <strong className="break">{c.code}</strong>
                    <Badge soon={!c.active}>{c.active ? "Actif" : "Désactivé"}</Badge>
                  </div>
                  <span className="muted small bp-tabular">
                    {c.percentOff > 0 ? `${c.percentOff} %` : fmtXaf(c.amountOffXaf)} · {c.redeemed}
                    {c.maxRedemptions ? ` / ${c.maxRedemptions}` : ""} utilisations · {c.validUntil ? `jusqu'au ${fmtDateTime(c.validUntil)}` : "sans échéance"} ·{" "}
                    {c.plans.length ? c.plans.join(", ") : "tous les plans payants"}
                  </span>
                  {canWrite && <CouponToggle code={c.code} active={c.active} />}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
