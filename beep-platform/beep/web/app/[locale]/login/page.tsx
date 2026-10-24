import { notFound } from "next/navigation";
import { Alert } from "@beep/ui";
import { AuthShell } from "@/components/AuthShell";
import { LoginForm, MagicLinkForm, OAuthButtons } from "@/components/forms";
import { oauthProviders } from "@/lib/core";
import { getConsole, getDictionary, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Login({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ error?: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { error } = await searchParams;
  const dict = getDictionary(locale);
  const t = getConsole(locale);
  const providers = await oauthProviders();
  return (
    <AuthShell dict={dict} locale={locale} path="/login" title={dict.auth.login.title} sub={dict.auth.login.sub}>
      {error && <Alert tone="danger">{t.errors[error] ?? t.errors.generic}</Alert>}
      <OAuthButtons providers={providers} locale={locale} t={t} />
      {providers.length > 0 && <p className="auth-or">{t.passwordless.or}</p>}
      <LoginForm dict={dict} locale={locale} />
      <details className="auth-magic">
        <summary>{t.passwordless.magicTitle}</summary>
        <MagicLinkForm dict={dict} locale={locale} t={t} />
      </details>
    </AuthShell>
  );
}
