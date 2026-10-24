import { notFound } from "next/navigation";
import { Alert } from "@beep/ui";
import { AuthShell } from "@/components/AuthShell";
import { OAuthButtons, SignupForm } from "@/components/forms";
import { oauthProviders } from "@/lib/core";
import { getConsole, getDictionary, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Signup({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  const t = getConsole(locale);
  const providers = await oauthProviders();
  return (
    <AuthShell dict={dict} locale={locale} path="/signup" title={dict.auth.signup.title} sub={dict.auth.signup.sub}>
      <Alert tone="info">{t.passwordless.inviteNote}</Alert>
      <OAuthButtons providers={providers} locale={locale} t={t} />
      {providers.length > 0 && <p className="auth-or">{t.passwordless.or}</p>}
      <SignupForm dict={dict} locale={locale} />
    </AuthShell>
  );
}
