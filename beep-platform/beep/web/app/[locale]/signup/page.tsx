import { notFound } from "next/navigation";
import { AuthShell } from "@/components/AuthShell";
import { SignupForm } from "@/components/forms";
import { getDictionary, isLocale } from "@/lib/i18n";

export default async function Signup({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  return (
    <AuthShell dict={dict} locale={locale} path="/signup" title={dict.auth.signup.title} sub={dict.auth.signup.sub}>
      <SignupForm dict={dict} locale={locale} />
    </AuthShell>
  );
}
