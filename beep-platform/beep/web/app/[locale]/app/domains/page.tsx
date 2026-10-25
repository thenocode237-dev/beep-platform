import { notFound } from "next/navigation";
import { DomainsPanel } from "@/components/DomainsPanel";
import type { DomainsView, WorkspaceView } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getDomains } from "@/lib/dictionaries/domains";
import { getConsole, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function Domains({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const [res, ws] = await Promise.all([coreGet<DomainsView>("/domains"), coreGet<WorkspaceView>("/workspace")]);
  if (!res.data) notFound();
  const limit = ws.data?.entitlements["domains.max"] ?? 0;
  const canAdd = limit === -1 || res.data.items.length < limit;
  return <DomainsPanel view={res.data} t={getConsole(locale)} d={getDomains(locale)} canAdd={canAdd} />;
}
