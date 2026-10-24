import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { VerifyEmail } from "@/components/forms";
import { getDictionary, isLocale } from "@/lib/i18n";

export default async function Verify({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ token?: string }> }) {
  const { locale } = await params;
  const { token } = await searchParams;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/verify-email" title={dict.auth.verify.title}>
      <VerifyEmail dict={dict} locale={locale} token={token} />
    </AuthShell>
  );
}
