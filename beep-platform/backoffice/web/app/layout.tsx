import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import type { ReactNode } from "react";
import "@beep/ui/styles.css";
import "./console.css";

const body = localFont({
  src: "../../../packages/design-tokens/assets/fonts/dm-sans-latin.woff2",
  variable: "--font-body",
  display: "swap",
  weight: "100 1000",
});
const display = localFont({
  src: "../../../packages/design-tokens/assets/fonts/manrope-latin.woff2",
  variable: "--font-display",
  display: "swap",
  weight: "200 800",
});

export const metadata: Metadata = {
  title: "BeepClick · Back office",
  robots: { index: false, follow: false }, // outil interne : jamais indexé
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="fr" className={`${body.variable} ${display.variable}`}>
      <body>
        <a className="bp-skip-link" href="#main">
          Aller au contenu
        </a>
        {children}
      </body>
    </html>
  );
}
