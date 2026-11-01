import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { isLocale, href } from "@/lib/i18n";
export function LegalPage({
  locale,
  kind,
}: {
  locale: string;
  kind: "privacy" | "terms" | "notice";
}) {
  if (!isLocale(locale)) notFound();
  const en = locale === "en";
  const titles = {
    privacy: en ? "Privacy" : "Confidentialité",
    terms: en ? "Terms of service" : "Conditions d’utilisation",
    notice: en ? "Publisher information" : "Mentions légales",
  };
  const privacy = en
    ? [
        [
          "Your account data",
          "Beepclick stores your email, account preferences, security settings and the links, QR codes, pages and domains you create. Security secrets are never included in your portable export.",
        ],
        [
          "Usage statistics",
          "Clicks and scans are analysed by device, country and referrer. IP addresses are not stored in analytics events. Visitor identifiers are salted daily; salts are removed after two days. Raw events are retained for up to 13 months, and aggregates according to the plan.",
        ],
        [
          "Your choices",
          "From account settings, export your data as JSON or request permanent deletion. Deletion requires a fresh email confirmation and, when enabled, your authenticator code. Account data and analytics are removed; accounting records are kept separately for applicable obligations.",
        ],
        [
          "Sessions and external services",
          "Essential session cookies keep you signed in. OAuth providers are contacted only when you choose their login option. Payments use the configured payment provider. When URL safety checking is enabled, destination URLs are sent to the safety provider.",
        ],
        [
          "Backups",
          "Deleted data may remain in protected backups until their retention period ends. Any restoration must reapply recorded erasures before the service is reopened.",
        ],
      ]
    : [
        [
          "Les données de votre compte",
          "Beepclick conserve votre e-mail, vos préférences, vos réglages de sécurité ainsi que vos liens, QR codes, pages et domaines. Les secrets de sécurité sont exclus de l’export portable.",
        ],
        [
          "Les statistiques d’usage",
          "Clics et scans sont analysés par appareil, pays et référent. Les adresses IP ne sont pas stockées dans les événements statistiques. Les identifiants visiteurs sont salés quotidiennement ; les sels sont supprimés après deux jours. Les événements bruts sont conservés au plus 13 mois, les agrégats selon l’offre.",
        ],
        [
          "Vos choix",
          "Depuis les paramètres, exportez vos données en JSON ou demandez la suppression définitive. Elle exige une confirmation e-mail récente et, si activé, votre code de sécurité. Les données du compte et les statistiques sont retirées ; les pièces comptables sont conservées séparément selon les obligations applicables.",
        ],
        [
          "Sessions et services externes",
          "Des cookies de session essentiels maintiennent votre connexion. Les fournisseurs OAuth sont contactés lorsque vous choisissez leur mode de connexion. Les paiements passent par le fournisseur configuré. Lorsque le contrôle de sécurité des URL est activé, les destinations sont transmises au fournisseur de sécurité.",
        ],
        [
          "Sauvegardes",
          "Les données supprimées peuvent subsister dans les sauvegardes protégées jusqu’à leur échéance de rétention. Une restauration doit réappliquer les suppressions enregistrées avant réouverture du service.",
        ],
      ];
  const terms = en
    ? [
        [
          "Using Beepclick",
          "Use Beepclick only for lawful content. Phishing, malware, spam, impersonation and attempts to bypass moderation are prohibited. You remain responsible for the destinations you share.",
        ],
        [
          "Plans and payments",
          "Starter is free within its published quotas. Paid plans are prepaid; review the quote, period and tax before confirmation. A plan upgrade is immediate with a credit for remaining paid time. A downgrade applies at the end of the paid period.",
        ],
        [
          "Expiry and excess links",
          "After the grace period, your account returns to Starter. Excess links pause 30 days after the unpaid expiry and become eligible for deletion after 90 days. A successful advance notice must precede deletion by at least seven days. Renew or export your data before the deadline.",
        ],
        [
          "Abuse and availability",
          "The team may disable unsafe links, block destinations or suspend abusive accounts. App opening depends on third-party apps, browsers and devices. Consult service status and report suspicious links using the public form.",
        ],
      ]
    : [
        [
          "Utiliser Beepclick",
          "Utilisez Beepclick pour des contenus licites. Phishing, logiciels malveillants, spam, usurpation et contournement de la modération sont interdits. Vous restez responsable des destinations partagées.",
        ],
        [
          "Offres et paiements",
          "Starter est gratuit dans les quotas publiés. Les offres payantes sont prépayées ; vérifiez le devis, la période et les taxes avant confirmation. Une montée en gamme est immédiate avec crédit du temps payé restant. Une baisse d’offre prend effet à la fin de la période payée.",
        ],
        [
          "Échéance et liens en excès",
          "Après la période de grâce, le compte repasse en Starter. Les liens en excès sont mis en pause 30 jours après l’échéance impayée et deviennent supprimables après 90 jours. Un préavis effectivement envoyé doit précéder la suppression d’au moins sept jours. Renouvelez ou exportez vos données avant cette date.",
        ],
        [
          "Abus et disponibilité",
          "L’équipe peut désactiver les liens dangereux, bloquer des destinations ou suspendre des comptes abusifs. L’ouverture des applications dépend des apps, navigateurs et appareils tiers. Consultez l’état des services et signalez les liens suspects par le formulaire public.",
        ],
      ];
  const publisher = process.env.LEGAL_PUBLISHER,
    contact = process.env.LEGAL_CONTACT,
    address = process.env.LEGAL_ADDRESS,
    registration = process.env.LEGAL_REGISTRATION,
    host = process.env.LEGAL_HOST;
  return (
    <PublicPage locale={locale} path={`/legal/${kind}`} title={titles[kind]}>
      <nav className="bp-row" aria-label={en ? "Legal pages" : "Pages légales"}>
        {Object.entries(titles).map(([key, label]) => (
          <a
            className="text-link"
            key={key}
            href={href(locale, `/legal/${key}`)}
          >
            {label}
          </a>
        ))}
      </nav>
      {kind === "notice" ? (
        <section className="bp-card bp-stack">
          <h2>{en ? "Publisher" : "Éditeur"}</h2>
          {publisher && address && registration && contact && host ? (
            <dl>
              <dt>{en ? "Entity" : "Entité"}</dt>
              <dd>{publisher}</dd>
              <dt>{en ? "Address" : "Adresse"}</dt>
              <dd>{address}</dd>
              <dt>{en ? "Registration" : "Immatriculation"}</dt>
              <dd>{registration}</dd>
              <dt>Contact</dt>
              <dd>{contact}</dd>
              <dt>{en ? "Hosting" : "Hébergement"}</dt>
              <dd>{host}</dd>
            </dl>
          ) : (
            <p>
              {en
                ? "Publisher details are awaiting validation. Public launch has not been approved."
                : "Les coordonnées de l’éditeur sont en attente de validation. Le lancement public n’est pas encore validé."}
            </p>
          )}
        </section>
      ) : (
        (kind === "privacy" ? privacy : terms).map(([title, body]) => (
          <section className="bp-stack public-prose" key={title}>
            <h2>{title}</h2>
            <p>{body}</p>
          </section>
        ))
      )}
      {contact && <p>Contact : {contact}</p>}
    </PublicPage>
  );
}
