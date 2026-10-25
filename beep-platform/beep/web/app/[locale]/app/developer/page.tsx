import { notFound } from "next/navigation";
import {
  DeveloperPanel,
  type DeveloperView,
  type ConversionView,
} from "@/components/DeveloperPanel";
import { coreGet } from "@/lib/core";
import { isLocale } from "@/lib/i18n";
import type { LinksPage, WorkspaceView } from "@/lib/api";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ days?: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const query = await searchParams,
    value = Number(query.days ?? 30),
    days = [7, 30, 90, 365, 36500].includes(value) ? value : 30;
  const [view, stats, links, ws] = await Promise.all([
    coreGet<DeveloperView>("/developer"),
    coreGet<ConversionView>(`/conversions/analytics?days=${days}`),
    coreGet<LinksPage>("/links?limit=100"),
    coreGet<WorkspaceView>("/workspace"),
  ]);
  if (!view.data || !ws.data)
    return (
      <p role="alert">
        {locale === "en"
          ? "Service unavailable. Please refresh."
          : "Service indisponible. Actualisez la page."}
      </p>
    );
  return (
    <DeveloperPanel
      view={view.data}
      analytics={stats.data}
      links={links.data?.items ?? []}
      entitlements={ws.data.entitlements}
      en={locale === "en"}
      days={days}
    />
  );
}
