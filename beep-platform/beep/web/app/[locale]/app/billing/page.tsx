import { notFound } from "next/navigation";
import { BillingPanel } from "@/components/BillingPanel";
import type { BillingView } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getBilling } from "@/lib/dictionaries/billing";
import { getConsole, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Billing({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const res = await coreGet<BillingView>("/billing");
  if (!res.data) notFound();
  return <BillingPanel view={res.data} t={getConsole(locale)} b={getBilling(locale)} locale={locale} />;
}
