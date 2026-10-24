import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { connection } from "next/server";
import { Badge, Icon, type IconName } from "@beep/ui";
import { SiteHeader, SiteFooter } from "@/components/SiteChrome";
import { LogoutButton, TotpSetup } from "@/components/forms";
import { experience } from "@/lib/experience";
import { getDictionary, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";
type Me = {
  id: string;
  email: string;
  emailVerified: boolean;
  totpEnabled: boolean;
};

async function fetchMe(): Promise<Me | null> {
  const base = process.env.CORE_INTERNAL_URL ?? "http://core:8080/v1";
  const cookie = (await cookies()).toString();
  if (!cookie) return null;
  try {
    const res = await fetch(`${base}/auth/me`, {
      headers: { cookie },
      cache: "no-store",
    });
    return res.ok ? ((await res.json()) as Me) : null;
  } catch {
    return null;
  }
}

export default async function Dashboard({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  await connection();
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const me = await fetchMe();
  if (!me) redirect(href(locale, "/login"));
  const dict = getDictionary(locale);
  const c = experience(locale);
  return (
    <>
      <SiteHeader dict={dict} locale={locale} path="/app" />
      <main id="main" className="workspace">
        <div className="bp-container">
          <div className="workspace__heading">
            <div>
              <h1>{c.workspaceTitle}</h1>
              <p className="muted">{c.workspaceSub}</p>
            </div>
            <LogoutButton dict={dict} locale={locale} />
          </div>
          <div className="workspace__grid">
            <section
              className="bp-card bp-card--lg bp-stack account-card"
              aria-labelledby="account-title"
            >
              <span className="workspace__symbol">
                <Icon name="bio" />
              </span>
              <h2 id="account-title">{c.account}</h2>
              <p className="account-card__email">{me.email}</p>
              <p className="bp-row">
                <Icon name={me.emailVerified ? "check" : "info"} size={17} />
                {me.emailVerified ? c.accountVerified : c.accountUnverified}
              </p>
              <p className="muted">{dict.app.intro}</p>
            </section>
            <section
              className="bp-card bp-card--lg bp-stack security-card"
              aria-labelledby="security-title"
            >
              <span className="workspace__symbol">
                <Icon name="shield" />
              </span>
              <h2 id="security-title">{dict.app.security}</h2>
              <p className="muted">{c.securityText}</p>
              <h3>{dict.app.totpTitle}</h3>
              <TotpSetup dict={dict} enabled={me.totpEnabled} />
            </section>
          </div>
          <section
            className="bp-card bp-card--lg bp-stack upcoming-card"
            aria-labelledby="upcoming-title"
          >
            <h2 id="upcoming-title">{c.workspaceFuture}</h2>
            <p className="muted">{c.workspaceFutureText}</p>
            <ul className="upcoming-tools">
              {dict.home.features.map((f) => (
                <li key={f.title}>
                  <Icon name={f.icon as IconName} size={20} />
                  <span>{f.title}</span>
                  <Badge soon>{dict.common.soon}</Badge>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </main>
      <SiteFooter dict={dict} locale={locale} />
    </>
  );
}
