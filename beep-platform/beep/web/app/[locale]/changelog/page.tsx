import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/changelog");
import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { EditorialBlocks } from "@/components/EditorialBlocks";
import { editorial } from "@/lib/editorial";
import { isLocale } from "@/lib/i18n";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { items, available } = await editorial("announcement", locale);
  return (
    <PublicPage
      locale={locale}
      path="/changelog"
      title={
        locale === "en" ? "What is new in Beep." : "Les nouveautés de Beep."
      }
    >
      {!available && (
        <p role="alert">
          {locale === "en"
            ? "Content unavailable. Please try again."
            : "Contenu indisponible. Réessayez."}
        </p>
      )}
      {available && items.length === 0 && (
        <p>
          {locale === "en"
            ? "Updates will appear here."
            : "Les prochaines mises à jour apparaîtront ici."}
        </p>
      )}
      {items.map((p) => (
        <article className="bp-card bp-stack" key={p.slug}>
          <h2>{p.title}</h2>
          <time>{p.published_at.slice(0, 10)}</time>
          <EditorialBlocks body={p.body} />
        </article>
      ))}
    </PublicPage>
  );
}
