import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { LoginForm } from "@/components/forms";
import { getDictionary, isLocale } from "@/lib/i18n";

export default async function Login({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/login" title={dict.auth.login.title} sub={dict.auth.login.sub}>
      <LoginForm dict={dict} locale={locale} />
    </AuthShell>
  );
}
