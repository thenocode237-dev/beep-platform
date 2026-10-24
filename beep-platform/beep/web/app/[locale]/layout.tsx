import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import "@beep/ui/styles.css";
import "../site.css";
import { getDictionary, isLocale, locales } from "@/lib/i18n";

const body = localFont({
  src: "../../../../packages/design-tokens/assets/fonts/dm-sans-latin.woff2",
  variable: "--font-body",
  display: "swap",
  weight: "100 1000",
});
const display = localFont({
  src: "../../../../packages/design-tokens/assets/fonts/manrope-latin.woff2",
  variable: "--font-display",
  display: "swap",
  weight: "200 800",
});

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1, // jamais de zoom désactivé
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F8F7F2" },
    { media: "(prefers-color-scheme: dark)", color: "#18231C" },
  ],
};

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = getDictionary(locale);
  return {
    metadataBase: new URL(
      process.env.BEEP_PUBLIC_URL ?? "http://beep.localhost",
    ),
    title: dict.meta.title,
    description: dict.meta.description,
    alternates: { languages: { fr: "/", en: "/en" } },
    openGraph: {
      title: dict.meta.title,
      description: dict.meta.description,
      locale: locale === "fr" ? "fr_FR" : "en_US",
      type: "website",
    },
  };
}

export default async function RootLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <html lang={locale} className={`${body.variable} ${display.variable}`}>
      <body>
        <a className="bp-skip-link" href="#main">
          {dict.common.skip}
        </a>
        {children}
      </body>
    </html>
  );
}
