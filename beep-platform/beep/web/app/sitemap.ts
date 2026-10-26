import type { MetadataRoute } from "next";
import { editorial } from "@/lib/editorial";
import { href, locales } from "@/lib/i18n";
import { languageAlternates, publicPages, siteUrl } from "@/lib/seo";

export const dynamic = "force-dynamic";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const pages: MetadataRoute.Sitemap = Object.keys(publicPages).flatMap((path) =>
    locales.map((locale) => ({
      url: new URL(href(locale, path), siteUrl()).href,
      alternates: { languages: languageAlternates(path) },
    })),
  );
  const articles = await Promise.all(locales.map(async (locale) => {
    const { items } = await editorial("blog", locale);
    return items.filter((article) => /^[a-z0-9][a-z0-9-]{0,79}$/.test(article.slug)).map((article) => ({
      url: new URL(href(locale, `/blog/${article.slug}`), siteUrl()).href,
      ...(Number.isFinite(Date.parse(article.published_at)) ? { lastModified: article.published_at } : {}),
    }));
  }));
  return [...pages, ...articles.flat()];
}
