import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { ResetForm } from "@/components/forms";
import { getDictionary, isLocale } from "@/lib/i18n";

export default async function Reset({ params, searchParams }: { params: Promise<{ locale: string }>; searchParams: Promise<{ token?: string }> }) {
  const { locale } = await params;
  const { token } = await searchParams;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/reset-password" title={dict.auth.reset.title}>
      <ResetForm dict={dict} locale={locale} token={token} />
    </AuthShell>
  );
}
