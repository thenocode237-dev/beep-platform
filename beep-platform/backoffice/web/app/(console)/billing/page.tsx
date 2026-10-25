import { Badge } from "@beep/ui";
import { Unavailable } from "@/components/Unavailable";
import { getBillingSummary, getPayments, getSubscriptions, getUpcoming } from "@/lib/api";
import { cycleLabels, fmtDateTime, fmtXaf, methodLabels, paymentLabels, subscriptionLabels } from "@/lib/format";

const tabs = [
  ["echeances", "Échéances à venir"],
  ["impayes", "Impayés"],
  ["paiements", "Paiements"],
  ["abonnements", "Abonnements actifs"],
] as const;

export default async function Billing({ searchParams }: { searchParams: Promise<{ vue?: string; cursor?: string; status?: string }> }) {
  const { vue = "echeances", cursor, status } = await searchParams;
  const summary = await getBillingSummary();
  const subs =
    vue === "impayes" ? await getSubscriptions("grace") : vue === "abonnements" ? await getSubscriptions("active") : vue === "echeances" ? await getUpcoming(30) : undefined;
  const pays = vue === "paiements" ? await getPayments({ cursor, status }) : undefined;
  const s = summary.data;

  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Facturation.</h1>
          <p>Abonnements prépayés, paiements Mobile Money, cartes et manuels. Les paiements manuels et changements de plan se font depuis la fiche d'un utilisateur.</p>
        </div>
      </div>
      {!s ? (
        <Unavailable status={summary.status} what="la facturation" />
      ) : (
        <ul className="kpis">
          <li className="bp-card">
            <span className="kpi__label">MRR</span>
            <strong className="kpi__value bp-tabular">{fmtXaf(s.mrrXaf)}</strong>
          </li>
          <li className="bp-card">
            <span className="kpi__label">ARR</span>
            <strong className="kpi__value bp-tabular">{fmtXaf(s.arrXaf)}</strong>
          </li>
          <li className="bp-card">
            <span className="kpi__label">Encaissé sur 30 jours</span>
            <strong className="kpi__value bp-tabular">{fmtXaf(s.revenue30dXaf)}</strong>
          </li>
          <li className="bp-card">
            <span className="kpi__label">Abonnements actifs / essais</span>
            <strong className="kpi__value bp-tabular">{s.active} / {s.trialing}</strong>
          </li>
          <li className="bp-card">
            <span className="kpi__label">Impayés (grâce)</span>
            <strong className="kpi__value bp-tabular">{s.grace}</strong>
          </li>
          <li className="bp-card">
            <span className="kpi__label">Paiements en attente / échoués</span>
            <strong className="kpi__value bp-tabular">{s.pendingPayments} / {s.failedPayments}</strong>
          </li>
        </ul>
      )}

      <nav className="bp-row" aria-label="Vues de facturation">
        {tabs.map(([key, label]) => (
          <a key={key} className={`bp-btn ${vue === key ? "" : "bp-btn--secondary"}`} href={`/billing?vue=${key}`} aria-current={vue === key ? "page" : undefined}>
            {label}
          </a>
        ))}
      </nav>

      {subs && (
        <section className="bp-card bp-stack" aria-label="Abonnements">
          {!subs.data ? (
            <Unavailable status={subs.status} what="les abonnements" />
          ) : subs.data.items.length === 0 ? (
            <p className="muted">Rien à afficher.</p>
          ) : (
            <ul className="history">
              {subs.data.items.map((x) => (
                <li key={x.workspaceId}>
                  <div className="link-row">
                    <a className="break" href={`/users?q=${encodeURIComponent(x.ownerEmail ?? "")}`}>
                      <strong>{x.ownerEmail ?? x.workspaceName}</strong>
                    </a>
                    <Badge soon={x.status !== "active"}>{subscriptionLabels[x.status]}</Badge>
                  </div>
                  <span className="muted small bp-tabular">
                    {x.plan} {x.cycle ? `· ${cycleLabels[x.cycle]}` : "· essai"} · {x.monthlyRateXaf > 0 ? `${fmtXaf(x.monthlyRateXaf)} / mois · ` : ""}échéance {fmtDateTime(x.periodEnd)}
                    {x.status === "grace" ? ` · accès jusqu'au ${fmtDateTime(x.graceEnd)}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {pays && (
        <section className="bp-card bp-stack" aria-label="Paiements">
          <form className="search" method="get" action="/billing">
            <input type="hidden" name="vue" value="paiements" />
            <label className="bp-field__label" htmlFor="pay-status">
              État
            </label>
            <select id="pay-status" name="status" className="bp-input" defaultValue={status ?? ""}>
              <option value="">Tous</option>
              {Object.entries(paymentLabels).map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
            <button className="bp-btn bp-btn--secondary" type="submit">
              Filtrer
            </button>
          </form>
          {!pays.data ? (
            <Unavailable status={pays.status} what="les paiements" />
          ) : pays.data.items.length === 0 ? (
            <p className="muted">Aucun paiement.</p>
          ) : (
            <ul className="history">
              {pays.data.items.map((p) => (
                <li key={p.id}>
                  <div className="link-row">
                    <strong className="bp-tabular">{fmtXaf(p.amountXaf)}</strong>
                    <Badge soon={p.status !== "succeeded"}>{paymentLabels[p.status]}</Badge>
                  </div>
                  <span className="muted small bp-tabular">
                    {p.ownerEmail ?? p.workspaceName} · {p.plan} {cycleLabels[p.cycle]} · {methodLabels[p.method]} ({p.provider}) · {fmtDateTime(p.createdAt)}
                    {p.discountXaf > 0 ? ` · remise ${fmtXaf(p.discountXaf)}${p.coupon ? ` (${p.coupon})` : ""}` : ""}
                    {p.creditXaf > 0 ? ` · crédit ${fmtXaf(p.creditXaf)}` : ""}
                    {p.note ? ` · ${p.note}` : ""}
                    {p.failureReason ? ` · ${p.failureReason}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {pays.data?.nextCursor && (
            <a className="bp-btn bp-btn--secondary" href={`/billing?vue=paiements&cursor=${encodeURIComponent(pays.data.nextCursor)}${status ? `&status=${status}` : ""}`}>
              Plus anciens
            </a>
          )}
        </section>
      )}
    </div>
  );
}
