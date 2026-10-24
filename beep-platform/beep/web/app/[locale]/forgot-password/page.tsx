import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { ForgotForm } from "@/components/forms";
import { getDictionary, isLocale } from "@/lib/i18n";

export default async function Forgot({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/forgot-password" title={dict.auth.forgot.title} sub={dict.auth.forgot.sub}>
      <ForgotForm dict={dict} locale={locale} />
    </AuthShell>
  );
}
