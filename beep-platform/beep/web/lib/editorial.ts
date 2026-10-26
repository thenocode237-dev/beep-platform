import "server-only";
import { cache } from "react";
import type { Locale } from "@/lib/i18n";
export type Article = {
  kind: string;
  slug: string;
  locale: string;
  title: string;
  body: string;
  version: number;
  published_at: string;
};
export const editorial = cache(async function editorial(
  kind: "blog" | "roadmap" | "announcement",
  locale: Locale | "es" | "pt",
  slug?: string,
): Promise<{ items: Article[]; available: boolean }> {
  try {
    const q = new URLSearchParams({ kind, locale });
    if (slug) q.set("slug", slug);
    const r = await fetch(
      `${process.env.CORE_INTERNAL_URL ?? "http://core:8080/v1"}/content?${q}`,
      { cache: "no-store", signal: AbortSignal.timeout(4000) },
    );
    if (!r.ok) return { items: [], available: false };
    return {
      items: ((await r.json()) as { items: Article[] }).items,
      available: true,
    };
  } catch {
    return { items: [], available: false };
  }
});

export const editorialLanguage=(raw:string|undefined,fallback:Locale):Locale|"es"|"pt"=>raw==="es"||raw==="pt"||raw==="fr"||raw==="en"?raw:fallback;
