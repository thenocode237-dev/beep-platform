import { notFound } from "next/navigation";
import { Icon } from "@beep/ui";
import { PrivacyPanel } from "@/components/PrivacyPanel";
import { LogoutButton, TotpSetup } from "@/components/forms";
import type { Me } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { experience } from "@/lib/experience";
import { getConsole, getDictionary, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Settings({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  const t = getConsole(locale);
  const c = experience(locale);
  const me = await coreGet<Me>("/auth/me");
  if (!me.data) notFound();
  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{t.settings.title}</h1>
        <p className="muted">{t.settings.sub}</p>
      </div>
      <a className="bp-btn bp-btn--secondary" href={href(locale,"/app/support")}>{locale==="fr"?"Contacter le support":"Contact support"}</a>
      <PrivacyPanel locale={locale} totpEnabled={me.data.totpEnabled}/>
      <div className="app-grid">
        <section className="bp-card bp-card--lg bp-stack account-card" aria-labelledby="account-title">
          <h2 id="account-title">{c.account}</h2>
          <p className="account-card__email">{me.data.email}</p>
          <p className="bp-row">
            <Icon name={me.data.emailVerified ? "check" : "info"} size={17} />
            {me.data.emailVerified ? c.accountVerified : c.accountUnverified}
          </p>
          {!me.data.hasPassword && (
            <p className="muted small">
              <a href={href(locale, "/forgot-password")}>{dict.auth.forgot.title}</a>
            </p>
          )}
          <LogoutButton dict={dict} locale={locale} />
        </section>
        <section className="bp-card bp-card--lg bp-stack security-card" aria-labelledby="security-title">
          <h2 id="security-title">{dict.app.security}</h2>
          <p className="muted">{c.securityText}</p>
          <h3>{dict.app.totpTitle}</h3>
          <TotpSetup dict={dict} enabled={me.data.totpEnabled} />
        </section>
      </div>
    </div>
  );
}
