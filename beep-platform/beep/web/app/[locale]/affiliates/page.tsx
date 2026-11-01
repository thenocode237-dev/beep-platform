import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/affiliates");
import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { href, isLocale } from "@/lib/i18n";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ ref?: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const en = locale === "en",
    ref = (await searchParams).ref,
    code = ref && /^[a-z0-9]{12}$/.test(ref) ? ref : "";
  return (
    <PublicPage
      locale={locale}
      path="/affiliates"
      title={
        en
          ? "Share Beepclick. Earn together."
          : "Recommandez Beepclick. Avancez ensemble."
      }
    >
      <section className="bp-card bp-stack">
        <h2>
          {en
            ? "20% for the first twelve months"
            : "20 % pendant les douze premiers mois"}
        </h2>
        <p>
          {en
            ? "Commissions apply to actual collected payments, approved after thirty days. Refunded and test payments are excluded. Manual payouts follow finance verification. Self-referrals are prohibited."
            : "Les commissions portent sur les paiements réellement encaissés et sont validées après trente jours. Les remboursements et paiements de test sont exclus. Les versements sont manuels après vérification par la finance. L’auto-parrainage est interdit."}
        </p>
        {code && (
          <p>
            {en ? "Referral code" : "Code de recommandation"} :{" "}
            <strong>{code}</strong>
          </p>
        )}
        <a
          className="bp-btn bp-btn--primary"
          href={href(locale, `/app/affiliates${code ? `?ref=${code}` : ""}`)}
        >
          {code
            ? en
              ? "Apply this referral code"
              : "Associer ce code à mon compte"
            : en
              ? "Join the program"
              : "Rejoindre le programme"}
        </a>
        <p>
          {en
            ? "Create your account first if needed, then enter the code before your first payment and within thirty days of workspace creation."
            : "Créez d’abord votre compte si nécessaire, puis saisissez le code avant votre premier paiement et dans les trente jours suivant la création du workspace."}
        </p>
      </section>
    </PublicPage>
  );
}
