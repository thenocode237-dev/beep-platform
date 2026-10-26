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
  const rows = en
    ? [
        ["links.stock_max", "Links in stock"],
        ["links.monthly_creations", "Link creations / month"],
        ["qr.stock_max", "QR codes in stock"],
        ["qr.monthly_creations", "QR creations / month"],
        ["api.rpm", "API requests / minute"],
        ["features.conversion", "Conversions (1 = available)"],
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
        ["features.conversion", "Conversions (1 = disponible)"],
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
        <div className="public-grid">
          {plans.map((p) => (
            <section className="bp-card bp-card--lg bp-stack" key={p.code}>
              <p className="eyebrow">{p.name}</p>
              <h2>
                {money(p.priceXaf)} <small>{en ? "/ month" : "/ mois"}</small>
              </h2>
              {!p.available && (
                <p className="muted">
                  {en
                    ? "Coming soon · 14-day trial from your account"
                    : "Bientôt · essai de 14 jours depuis votre compte"}
                </p>
              )}
              <dl className="public-plan-features">
                {rows.map(([key, label]) => (
                  <div key={key}>
                    <dt>{label}</dt>
                    <dd>
                      {p.entitlements[key] === undefined
                        ? "—"
                        : p.entitlements[key] === -1
                          ? en
                            ? "Unlimited"
                            : "Illimité"
                          : p.entitlements[key]}
                    </dd>
                  </div>
                ))}
              </dl>
              {p.priceXaf > 0 && (
                <p className="muted small">
                  {en ? "Quarterly" : "Trimestriel"} :{" "}
                  {money(p.priceQuarterlyXaf)}
                  <br />
                  {en ? "Annual" : "Annuel"} : {money(p.priceAnnualXaf)}
                </p>
              )}
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
            </section>
          ))}
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
