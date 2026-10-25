"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Alert, Badge, Button, TextField } from "@beep/ui";
import { send, type BillingPayment, type BillingQuote, type BillingView } from "@/lib/api";
import type { BillingDict } from "@/lib/dictionaries/billing";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";
import type { Locale } from "@/lib/i18n";

const money = (n: number) => `${new Intl.NumberFormat("fr-FR").format(n)} FCFA`;
const day = (iso: string, locale: Locale) => new Intl.DateTimeFormat(locale === "en" ? "en-GB" : "fr-FR", { dateStyle: "long", timeZone: "UTC" }).format(new Date(iso));

function priceOf(p: BillingView["plans"][number], cycle: string) {
  return cycle === "annual" ? p.priceAnnualXaf : cycle === "quarterly" ? p.priceQuarterlyXaf : p.priceXaf;
}

/** Page « Abonnement » : état, essai Elite, achat par Mobile Money ou carte avec suivi du paiement, historique et factures PDF. */
export function BillingPanel({ view, t, b, locale }: { view: BillingView; t: ConsoleDict; b: BillingDict; locale: Locale }) {
  const buyable = view.plans.filter((p) => p.priceXaf > 0);
  const [plan, setPlan] = useState(buyable.find((p) => p.public)?.code ?? buyable[0]?.code ?? "");
  const [cycle, setCycle] = useState("monthly");
  const [method, setMethod] = useState("mtn_momo");
  const [quote, setQuote] = useState<BillingQuote>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [paymentId, setPaymentId] = useState<string>();
  const [outcome, setOutcome] = useState<"pending" | "succeeded" | "failed">();
  const selected = view.plans.find((p) => p.code === plan);
  const err = (code?: string) => b.errors[code ?? ""] ?? t.errors[code ?? ""] ?? t.errors.generic;

  // Suivi du paiement : le serveur interroge l'agrégateur à chaque lecture ; on s'arrête dès qu'il est réglé.
  useEffect(() => {
    if (!paymentId || outcome !== "pending") return;
    let stopped = false;
    const tick = async () => {
      const r = await send<BillingPayment>("GET", `/billing/payments/${paymentId}`);
      if (stopped || !r.ok || !r.data) return;
      if (r.data.status === "succeeded") {
        setOutcome("succeeded");
        window.setTimeout(() => window.location.reload(), 1800);
      } else if (r.data.status === "failed") {
        setOutcome("failed");
      }
    };
    const id = window.setInterval(tick, 4000);
    return () => {
      stopped = true;
      window.clearInterval(id);
    };
  }, [paymentId, outcome]);

  const body = (e?: FormEvent<HTMLFormElement>) => {
    const f = e ? new FormData(e.currentTarget) : undefined;
    return { plan, cycle, method, phone: f ? String(f.get("phone") ?? "") : undefined, coupon: f ? String(f.get("coupon") ?? "") : undefined };
  };

  async function getQuote(form: HTMLFormElement) {
    setBusy(true);
    setError(undefined);
    const f = new FormData(form);
    const r = await send<BillingQuote>("POST", "/billing/quote", { plan, cycle, method, coupon: String(f.get("coupon") ?? "") });
    setBusy(false);
    if (!r.ok || !r.data) return setError(err(r.code));
    setQuote(r.data);
  }

  async function pay(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    const r = await send<{ payment: BillingPayment; redirectUrl: string | null }>("POST", "/billing/checkout", body(e));
    setBusy(false);
    if (!r.ok || !r.data) return setError(err(r.code));
    if (r.data.redirectUrl) return void (window.location.href = r.data.redirectUrl);
    setPaymentId(r.data.payment.id);
    setOutcome(r.data.payment.status === "succeeded" ? "succeeded" : "pending");
    if (r.data.payment.status === "succeeded") window.setTimeout(() => window.location.reload(), 1800);
  }

  async function startTrial() {
    setBusy(true);
    setError(undefined);
    const r = await send("POST", "/billing/trial");
    setBusy(false);
    if (!r.ok) return setError(err(r.code));
    window.location.reload();
  }

  const sub = view.subscription;
  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{b.title}</h1>
        <p className="muted">{b.sub}</p>
      </div>

      <section className="bp-card bp-card--lg bp-stack" aria-labelledby="bill-current">
        <h2 id="bill-current">{b.current}</h2>
        <p className="bp-row">
          <strong>{view.plan.name}</strong>
          {sub && sub.status !== "expired" && <Badge>{b.status[sub.status]}</Badge>}
        </p>
        {sub && sub.status === "grace" && <p>{`${b.status.grace} ${day(sub.graceEnd, locale)}`}</p>}
        {sub && (sub.status === "active" || sub.status === "trialing") && (
          <p className="muted bp-tabular">
            {b.validUntil} {day(sub.periodEnd, locale)}
          </p>
        )}
        {view.trialAvailable && (
          <div className="bp-stack">
            <h3>{b.trial.title}</h3>
            <p className="muted">{b.trial.text}</p>
            <Button variant="secondary" onClick={startTrial} loading={busy}>
              {b.trial.cta}
            </Button>
          </div>
        )}
      </section>

      {buyable.length > 0 && (
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="bill-buy">
          <h2 id="bill-buy">{b.plans}</h2>
          {error && <Alert tone="danger">{error}</Alert>}
          {outcome === "pending" && <Alert tone="info">{b.waiting}</Alert>}
          {outcome === "succeeded" && <Alert tone="success">{b.succeeded}</Alert>}
          {outcome === "failed" && <Alert tone="danger">{b.failed}</Alert>}
          <form className="bp-stack" onSubmit={pay}>
            <div className="plan-choice" role="radiogroup" aria-label={b.plans}>
              {buyable.map((p) => (
                <label key={p.code} className="check">
                  <input type="radio" name="plan" value={p.code} checked={plan === p.code} disabled={!p.public} onChange={() => { setPlan(p.code); setQuote(undefined); }} />{" "}
                  <strong>{p.name}</strong> — {money(p.priceXaf)} {b.perMonth} {!p.public && <Badge soon>{b.soon}</Badge>}
                </label>
              ))}
            </div>
            <div className="bp-field">
              <label className="bp-field__label" htmlFor="bill-cycle">{b.cycle}</label>
              <select id="bill-cycle" className="bp-input" value={cycle} onChange={(ev) => { setCycle(ev.target.value); setQuote(undefined); }}>
                {Object.entries(b.cycles).map(([k, l]) => (
                  <option key={k} value={k}>
                    {l}{selected ? ` — ${money(priceOf(selected, k))}` : ""}
                  </option>
                ))}
              </select>
            </div>
            <div className="bp-field">
              <label className="bp-field__label" htmlFor="bill-method">{b.method}</label>
              <select id="bill-method" className="bp-input" value={method} onChange={(ev) => setMethod(ev.target.value)}>
                {Object.entries(b.methods).map(([k, l]) => (
                  <option key={k} value={k}>{l}</option>
                ))}
              </select>
            </div>
            {method !== "card" && <TextField name="phone" label={b.phone} hint={b.phoneHint} inputMode="numeric" autoComplete="tel" pattern="[0-9 +.-]{8,20}" required />}
            <TextField name="coupon" label={b.coupon} autoComplete="off" onChange={() => setQuote(undefined)} />
            {quote && (
              <dl className="kv bp-tabular" aria-live="polite">
                <dt>{b.listPrice}</dt>
                <dd>{money(quote.listPriceXaf)}</dd>
                {quote.discountXaf > 0 && (<><dt>{b.discount}</dt><dd>−{money(quote.discountXaf)}</dd></>)}
                {quote.creditXaf > 0 && (<><dt>{b.credit}</dt><dd>−{money(quote.creditXaf)}</dd></>)}
                <dt>{b.total}</dt>
                <dd><strong>{money(quote.amountXaf)}</strong></dd>
                <dt>{b.newEnd}</dt>
                <dd>{day(quote.newPeriodEnd, locale)}</dd>
              </dl>
            )}
            <div className="bp-row">
              <Button type="button" variant="secondary" loading={busy} onClick={(ev) => getQuote(ev.currentTarget.form!)}>
                {b.quote}
              </Button>
              <Button type="submit" loading={busy} disabled={outcome === "pending"}>
                {method === "card" ? b.card : b.pay}
              </Button>
            </div>
          </form>
        </section>
      )}

      <div className="app-grid">
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="bill-history">
          <h2 id="bill-history">{b.history}</h2>
          {view.payments.length === 0 ? (
            <p className="muted">{b.noPayments}</p>
          ) : (
            <ul className="history">
              {view.payments.map((p) => (
                <li key={p.id}>
                  <div className="link-row">
                    <strong className="bp-tabular">{money(p.amountXaf)}</strong>
                    <Badge soon={p.status !== "succeeded"}>{b.status_payment[p.status]}</Badge>
                  </div>
                  <span className="muted small bp-tabular">
                    {day(p.createdAt, locale)} · {b.methods[p.method] ?? p.method}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="bp-card bp-card--lg bp-stack" aria-labelledby="bill-invoices">
          <h2 id="bill-invoices">{b.invoices}</h2>
          {view.invoices.length === 0 ? (
            <p className="muted">{b.noInvoices}</p>
          ) : (
            <ul className="history">
              {view.invoices.map((i) => (
                <li key={i.number}>
                  <div className="link-row">
                    <strong className="bp-tabular">{i.number}</strong>
                    <a className="text-link" href={`/v1/billing/invoices/${i.number}/pdf`}>{b.download}</a>
                  </div>
                  <span className="muted small bp-tabular">
                    {day(i.issuedAt, locale)} · {money(i.grossXaf)} ({b.vat} {money(i.vatXaf)})
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
