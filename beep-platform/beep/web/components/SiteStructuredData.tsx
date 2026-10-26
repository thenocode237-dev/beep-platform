import { href, type Locale } from "@/lib/i18n";
import { siteUrl } from "@/lib/seo";

export function SiteStructuredData({ locale }: { locale: Locale }) {
  const origin = siteUrl().origin;
  const data = {
    "@context": "https://schema.org",
    "@graph": [
      { "@type": "Organization", "@id": `${origin}/#organization`, name: "Beep", url: origin },
      {
        "@type": "WebSite", "@id": `${origin}/#website`, name: "Beep",
        url: new URL(href(locale), origin).href, inLanguage: locale,
        publisher: { "@id": `${origin}/#organization` },
      },
    ],
  };
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(data).replace(/</g, "\\u003c") }} />;
}
