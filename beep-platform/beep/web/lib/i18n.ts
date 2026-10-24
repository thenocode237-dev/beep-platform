import fr, { type Dict } from "./dictionaries/fr";
import en from "./dictionaries/en";

export const locales = ["fr", "en"] as const;
export type Locale = (typeof locales)[number];
export const defaultLocale: Locale = "fr";

const dictionaries: Record<Locale, Dict> = { fr, en };

export const isLocale = (v: string): v is Locale => (locales as readonly string[]).includes(v);
export const getDictionary = (locale: Locale): Dict => dictionaries[locale];

/** URL publique d'un chemin : français sans préfixe, anglais sous /en. */
export const href = (locale: Locale, path = "/") => {
  const p = path === "/" ? "" : path;
  return locale === defaultLocale ? p || "/" : `/en${p}`;
};
