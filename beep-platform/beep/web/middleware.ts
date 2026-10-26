import { NextResponse, type NextRequest } from "next/server";

/**
 * Routage des langues sans préfixe pour le français (langue par défaut) :
 *   /login     -> réécrit en interne vers /fr/login
 *   /en/login  -> servi tel quel
 *   /fr/login  -> redirigé vers /login (une seule URL canonique par page)
 * Le préfixe `/en` est routé vers beep-web par Caddy ; `en` est un slug réservé côté core.
 */
export function middleware(req: NextRequest) {
  const { pathname, search } = req.nextUrl;
  const first = pathname.split("/")[1];
  const privatePath = /^\/(?:(?:fr|en)\/)?(?:app|login|signup|forgot-password|reset-password|verify-email|report)(?:\/|$)/.test(pathname);
  const withIndexing = (response: NextResponse) => {
    if (privatePath) response.headers.set("X-Robots-Tag", "noindex, nofollow");
    return response;
  };

  if (first === "en") return withIndexing(NextResponse.next());

  if (first === "fr") {
    const url = req.nextUrl.clone();
    url.pathname = pathname.replace(/^\/fr/, "") || "/";
    return withIndexing(NextResponse.redirect(url, 308));
  }

  return withIndexing(NextResponse.rewrite(new URL(`/fr${pathname}${search}`, req.url)));
}

export const config = {
  matcher: ["/((?!_next/|favicon.ico|robots.txt|sitemap.xml|icon.svg|.*\\..*).*)"],
};
