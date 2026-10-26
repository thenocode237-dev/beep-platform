import { notFound } from "next/navigation";
import { coreGet } from "@/lib/core";
import { isLocale } from "@/lib/i18n";
import { EnterprisePanel } from "@/components/EnterprisePanel";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const view = await coreGet<Record<string, unknown>>("/enterprise");
  return (
    <EnterprisePanel
      initial={view.data ?? {}}
      status={view.status}
      en={locale === "en"}
    />
  );
}
