import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { EditorialBlocks } from "@/components/EditorialBlocks";
import { editorial, editorialLanguage } from "@/lib/editorial";
import { href, isLocale } from "@/lib/i18n";
import { pageMetadata, privateRobots } from "@/lib/seo";
import type { Metadata } from "next";
export const dynamic = "force-dynamic";
export async function generateMetadata({ params, searchParams }: {
  params: Promise<{ locale: string; slug: string }>;
  searchParams: Promise<{ lang?: string }>;
}): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale) || !/^[a-z0-9][a-z0-9-]{0,79}$/.test(slug)) notFound();
  const language = editorialLanguage((await searchParams).lang, locale);
  const { items, available } = await editorial("blog", language, slug);
  if (!available) return { robots: privateRobots };
  const article = items[0];
  if (!article) notFound();
  const description = article.body.replace(/^#+\s*/gm, "").replace(/\s+/g, " ").trim().slice(0, 160);
  // Translations can have different slugs: never invent hreflang counterparts.
  const metadata = pageMetadata(locale, `/blog/${slug}`, article.title, description, false);
  return {
    ...metadata,
    ...(language !== locale ? { robots: privateRobots } : {}),
    openGraph: { ...metadata.openGraph, type: "article", publishedTime: article.published_at, locale: article.locale },
  };
}
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string; slug: string }>;
  searchParams: Promise<{ lang?: string }>;
}) {
  const { locale, slug } = await params;
  if (!isLocale(locale) || !slug.match(/^[a-z0-9][a-z0-9-]{0,79}$/)) notFound();
  const { items, available } = await editorial(
    "blog",
    editorialLanguage((await searchParams).lang, locale),
    slug,
  );
  if (!available)
    return (
      <PublicPage locale={locale} path="/blog" title="Journal">
        <p role="alert">
          {locale === "en" ? "Please try again later." : "Réessayez plus tard."}
        </p>
      </PublicPage>
    );
  const p = items[0];
  if (!p) notFound();
  return (
    <PublicPage locale={locale} path={"/blog/" + slug} title={p.title}>
      <a className="text-link" href={href(locale, "/blog")}>
        {locale === "en" ? "Back to journal" : "Retour au journal"}
      </a>
      <div lang={p.locale}>
        <EditorialBlocks body={p.body} />
      </div>
    </PublicPage>
  );
}
