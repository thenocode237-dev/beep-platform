import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { MfaForm } from "@/components/forms";
import { getConsole, getDictionary, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Verify({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ token?: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { token } = await searchParams;
  const dict = getDictionary(locale);
  const t = getConsole(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/login/verify" title={t.passwordless.verifyTitle} sub={t.passwordless.verifySub}>
      <MfaForm dict={dict} locale={locale} t={t} token={token} />
    </AuthShell>
  );
}
