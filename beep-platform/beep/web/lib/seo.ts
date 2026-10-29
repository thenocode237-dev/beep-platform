import type { Metadata } from "next";
import { getDictionary, href, isLocale, type Locale } from "@/lib/i18n";

// Keep the same origin at build time (prerendered pages) and at runtime (SSR).
export function siteUrl() {
  return new URL(process.env.BEEP_PUBLIC_URL || "https://beepclick.app");
}

export const publicPages = {
  "/": {
    fr: ["Liens courts, QR codes et pages bio", "Créez des liens courts, des QR codes et une page bio avec BeepClick. Partagez sur tous vos canaux et mesurez vos clics, scans et conversions."],
    en: ["Short links, QR codes and bio pages", "Create short links, QR codes and a bio page with BeepClick. Share across your channels and measure clicks, scans and conversions."],
  },
  "/features": {
    fr: ["Fonctionnalités : liens courts, QR codes et analytics", "Découvrez les liens courts, QR codes dynamiques, pages bio, domaines personnalisés et statistiques BeepClick pour mieux partager et mesurer vos campagnes."],
    en: ["Features: short links, QR codes and analytics", "Explore BeepClick short links, dynamic QR codes, bio pages, custom domains and analytics to share your content and measure your campaigns."],
  },
  "/pricing": {
    fr: ["Tarifs et offres Starter, Pro et Elite", "Comparez les offres BeepClick Starter, Pro et Elite : liens courts, QR codes, pages bio, statistiques et outils d’équipe selon vos besoins."],
    en: ["Pricing and Starter, Pro and Elite plans", "Compare BeepClick Starter, Pro and Elite plans for short links, QR codes, bio pages, analytics and team tools that fit your needs."],
  },
  "/blog": {
    fr: ["Journal : conseils pour mieux partager vos liens", "Conseils et actualités BeepClick sur les liens courts, les QR codes, les pages bio et le suivi de vos campagnes marketing."],
    en: ["Journal: ideas for sharing better links", "BeepClick tips and news on short links, QR codes, bio pages and measuring your marketing campaigns."],
  },
  "/docs": {
    fr: ["Documentation API, événements et webhooks", "Intégrez BeepClick avec l’API publique : authentification, liens, événements de conversion, attribution et vérification des signatures de webhooks."],
    en: ["API, events and webhooks documentation", "Integrate BeepClick using the public API: authentication, links, conversion events, attribution and webhook signature verification."],
  },
  "/roadmap": {
    fr: ["Feuille de route", "Consultez la feuille de route publique de BeepClick et les évolutions prévues pour les liens, QR codes, pages bio et outils de mesure."],
    en: ["Product roadmap", "Explore the BeepClick public roadmap and planned improvements to links, QR codes, bio pages and measurement tools."],
  },
  "/changelog": {
    fr: ["Nouveautés et mises à jour", "Retrouvez les nouveautés publiées et les améliorations de BeepClick pour vos liens courts, QR codes et campagnes."],
    en: ["Changelog and product updates", "Read the latest published BeepClick updates and improvements for your short links, QR codes and campaigns."],
  },
  "/affiliates": {
    fr: ["Programme d’affiliation", "Découvrez le programme d’affiliation BeepClick, ses conditions et le suivi des recommandations depuis votre espace."],
    en: ["Affiliate programme", "Discover the BeepClick affiliate programme, its terms and how to track referrals from your workspace."],
  },
  "/status": {
    fr: ["État des services", "Consultez la disponibilité des services BeepClick : redirections, API et services associés."],
    en: ["Service status", "Check the availability of BeepClick redirects, API and related services."],
  },
  "/legal": {
    fr: ["Informations légales", "Consultez les mentions légales, les conditions d’utilisation et la politique de confidentialité de BeepClick."],
    en: ["Legal information", "Read BeepClick publisher information, terms of service and privacy policy."],
  },
  "/legal/notice": {
    fr: ["Mentions légales", "Informations sur l’éditeur, les coordonnées et l’hébergement du service BeepClick."],
    en: ["Publisher information", "Information about the BeepClick publisher, contact details and hosting."],
  },
  "/legal/privacy": {
    fr: ["Politique de confidentialité", "Découvrez comment BeepClick traite vos données de compte, statistiques, cookies de session et demandes d’export ou de suppression."],
    en: ["Privacy policy", "Learn how BeepClick handles account data, analytics, session cookies and data export or deletion requests."],
  },
  "/legal/terms": {
    fr: ["Conditions d’utilisation", "Consultez les conditions d’utilisation de BeepClick : partage de liens, offres, paiements, disponibilité et traitement des abus."],
    en: ["Terms of service", "Read the BeepClick terms of service covering link sharing, plans, payments, availability and abuse handling."],
  },
} as const;

export type PublicPath = keyof typeof publicPages;

export function languageAlternates(path: string) {
  return {
    fr: new URL(href("fr", path), siteUrl()).href,
    en: new URL(href("en", path), siteUrl()).href,
    "x-default": new URL(href("fr", path), siteUrl()).href,
  };
}

export const privateRobots: Metadata["robots"] = { index: false, follow: false };

export function pageMetadata(
  locale: Locale,
  path: string,
  title: string,
  description: string,
  translated = true,
): Metadata {
  const url = new URL(href(locale, path), siteUrl()).href;
  const fullTitle = `${title} | BeepClick`;
  const images = [{ url: new URL("/assets/workspace.webp", siteUrl()).href, width: 900, height: 1000, alt: "BeepClick" }];
  return {
    title: fullTitle,
    description,
    alternates: { canonical: url, ...(translated ? { languages: languageAlternates(path) } : {}) },
    openGraph: {
      title: fullTitle, description, url, siteName: "BeepClick", type: "website",
      locale: locale === "fr" ? "fr_FR" : "en_US",
      ...(translated ? { alternateLocale: [locale === "fr" ? "en_US" : "fr_FR"] } : {}),
      images,
    },
    twitter: { card: "summary_large_image", title: fullTitle, description, images },
  };
}

export function publicMetadata(path: PublicPath) {
  return async ({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> => {
    const { locale } = await params;
    if (!isLocale(locale)) return {};
    const [title, description] = publicPages[path][locale];
    return pageMetadata(locale, path, title, description);
  };
}

export function defaultMetadata(locale: Locale): Metadata {
  const dict = getDictionary(locale);
  return { metadataBase: siteUrl(), title: dict.meta.title, description: dict.meta.description };
}
