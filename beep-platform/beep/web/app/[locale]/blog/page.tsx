import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { editorial, editorialLanguage } from "@/lib/editorial";
import { href, isLocale } from "@/lib/i18n";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ lang?: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const language = editorialLanguage((await searchParams).lang, locale);
  const { items, available } = await editorial("blog", language);
  return (
    <PublicPage
      locale={locale}
      path="/blog"
      title={
        locale === "en"
          ? "Ideas for better sharing."
          : "Des idées pour mieux partager."
      }
    >
      <nav aria-label="Langue du journal">
        {[
          ["fr", "Français"],
          ["en", "English"],
          ["es", "Español"],
          ["pt", "Português"],
        ].map(([code, label]) => (
          <a
            className="bp-btn bp-btn--secondary"
            key={code}
            href={href(locale, "/blog") + "?lang=" + code}
            aria-current={language === code ? "page" : undefined}
          >
            {label}
          </a>
        ))}
      </nav>
      {items.length === 0 && available && (
        <p>
          {locale === "en"
            ? "No published articles in this language yet."
            : "Aucun article publié dans cette langue pour le moment."}
        </p>
      )}
      {!available && (
        <p role="alert">
          {locale === "en"
            ? "Journal unavailable. Please try again."
            : "Journal indisponible. Réessayez."}
        </p>
      )}
      {items.map((p) => (
        <article className="bp-card bp-stack" key={p.slug}>
          <time>{p.published_at.slice(0, 10)}</time>
          <h2>
            <a href={href(locale, "/blog/" + p.slug) + "?lang=" + language}>
              {p.title}
            </a>
          </h2>
          <a
            className="text-link"
            href={href(locale, "/blog/" + p.slug) + "?lang=" + language}
          >
            {locale === "en" ? "Read article" : "Lire l’article"}
          </a>
        </article>
      ))}
    </PublicPage>
  );
}
