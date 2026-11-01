import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/pricing");
import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { href, isLocale } from "@/lib/i18n";
export const dynamic = "force-dynamic";
type Plan = {
  code: string;
  name: string;
  priceXaf: number;
  priceQuarterlyXaf: number;
  priceAnnualXaf: number;
  available: boolean;
  entitlements: Record<string, number>;
};
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const en = locale === "en";
  let plans: Plan[] = [];
  try {
    const r = await fetch(
      `${process.env.CORE_INTERNAL_URL ?? "http://core:8080/v1"}/public/plans`,
      { cache: "no-store", signal: AbortSignal.timeout(4000) },
    );
    if (r.ok) plans = ((await r.json()) as { items: Plan[] }).items;
  } catch {}
  const money = (n: number) =>
    new Intl.NumberFormat(en ? "en" : "fr", {
      style: "currency",
      currency: "XAF",
      maximumFractionDigits: 0,
    }).format(n);
  const mark = (ok: boolean) => (
    <svg
      className={ok ? "plan-mark plan-mark--yes" : "plan-mark plan-mark--no"}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      role="img"
      aria-label={ok ? (en ? "Included" : "Inclus") : en ? "Not included" : "Non inclus"}
    >
      {ok ? <path d="M5 12.5l4.5 4.5L19 7.5" /> : <path d="M6 6l12 12M18 6L6 18" />}
    </svg>
  );
  const cell = (v: number | undefined, key: string) => {
    if (v === undefined || v === 0) return mark(false);
    if (v === -1) return en ? "Unlimited" : "Illimité";
    if (key === "features.conversion") return mark(true);
    return v;
  };
  const rows = en
    ? [
        ["links.stock_max", "Links in stock"],
        ["links.monthly_creations", "Link creations / month"],
        ["qr.stock_max", "QR codes in stock"],
        ["qr.monthly_creations", "QR creations / month"],
        ["api.rpm", "API requests / minute"],
        ["features.conversion", "Conversions"],
        ["members.max", "Team members"],
        ["bio.max", "Bio pages"],
        ["domains.max", "Custom domains"],
        ["analytics.retention_days", "Analytics (days)"],
      ]
    : [
        ["links.stock_max", "Liens en stock"],
        ["links.monthly_creations", "Créations de liens / mois"],
        ["qr.stock_max", "QR en stock"],
        ["qr.monthly_creations", "Créations de QR / mois"],
        ["api.rpm", "Requêtes API / minute"],
        ["features.conversion", "Conversions"],
        ["members.max", "Membres d’équipe"],
        ["bio.max", "Pages bio"],
        ["domains.max", "Domaines personnels"],
        ["analytics.retention_days", "Statistiques (jours)"],
      ];
  return (
    <PublicPage
      locale={locale}
      path="/pricing"
      title={
        en
          ? "Start small. Grow at your pace."
          : "Commencez simplement. Évoluez à votre rythme."
      }
      intro={
        en
          ? "Prepaid plans in FCFA. No automatic debit."
          : "Des offres prépayées en FCFA. Aucun prélèvement automatique."
      }
    >
      {plans.length === 0 ? (
        <p className="bp-card" role="alert">
          {en
            ? "Plans are temporarily unavailable. Please try again later."
            : "Les offres sont temporairement indisponibles. Réessayez plus tard."}
        </p>
      ) : (
        <div className="plan-compare-wrap">
          <table className="plan-compare">
            <caption className="sr-only">
              {en ? "Plan comparison" : "Comparaison des offres"}
            </caption>
            <thead>
              <tr>
                <td />
                {plans.map((p) => (
                  <th scope="col" key={p.code}>
                    <span className="eyebrow">{p.name}</span>
                    <span className="plan-compare__price">
                      {money(p.priceXaf)} <small>{en ? "/ month" : "/ mois"}</small>
                    </span>
                    {p.priceXaf > 0 && (
                      <small className="muted">
                        {en ? "Quarterly" : "Trimestriel"} {money(p.priceQuarterlyXaf)}
                        <br />
                        {en ? "Annual" : "Annuel"} {money(p.priceAnnualXaf)}
                      </small>
                    )}
                    {!p.available && (
                      <small className="muted">
                        {en ? "Coming soon · 14-day trial" : "Bientôt · essai de 14 jours"}
                      </small>
                    )}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(([key, label]) => (
                <tr key={key}>
                  <th scope="row">{label}</th>
                  {plans.map((p) => (
                    <td key={p.code}>{cell(p.entitlements[key], key)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td />
                {plans.map((p) => (
                  <td key={p.code}>
                    <a
                      className="bp-btn bp-btn--secondary"
                      href={href(locale, p.available ? "/signup" : "/app/billing")}
                    >
                      {p.available
                        ? (en ? "Choose " : "Choisir ") + p.name
                        : en
                          ? "Explore the trial"
                          : "Découvrir l’essai"}
                    </a>
                  </td>
                ))}
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <section className="bp-card bp-stack">
        <h2>
          {en
            ? "Pay the way you already do."
            : "Payez comme vous le faites déjà."}
        </h2>
        <p>
          {en
            ? "MTN Mobile Money, Orange Money and cards are supported by the payment gateway. Availability depends on country and provider configuration. The final amount and applicable taxes are shown before confirmation."
            : "MTN Mobile Money, Orange Money et cartes sont pris en charge par la passerelle de paiement. La disponibilité dépend du pays et de la configuration du fournisseur. Le montant final et les taxes applicables sont affichés avant confirmation."}
        </p>
        <p>
          {en
            ? "Clicks and scans are unlimited. Pro includes the public API (60 requests/minute). Elite includes conversions, webhooks, 600 API requests/minute and 500 link and QR creations per month. Pro includes 3 members, Zapier / Make and email Radar. Elite includes 15 members, all native integrations, customer insights and advanced bio blocks."
            : "Les clics et scans sont illimités. Pro inclut l’API publique (60 requêtes/minute). Elite inclut les conversions, les webhooks, 600 requêtes API/minute et 500 créations de liens et de QR par mois. Pro inclut 3 membres, Zapier / Make et Radar par e-mail. Elite inclut 15 membres, toutes les intégrations natives, les customer insights et les blocs bio avancés."}
        </p>
      </section>
    </PublicPage>
  );
}
