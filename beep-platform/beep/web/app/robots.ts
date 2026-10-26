import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/seo";

export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  return {
    // Auth pages stay crawlable so crawlers can read their noindex directive.
    rules: { userAgent: "*", allow: "/", disallow: ["/v1/", "/internal/", "/actuator/"] },
    sitemap: new URL("/sitemap.xml", siteUrl()).href,
  };
}
