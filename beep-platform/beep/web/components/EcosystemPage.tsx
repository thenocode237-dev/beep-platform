import { notFound } from "next/navigation";
import { coreGet } from "@/lib/core";
import { isLocale } from "@/lib/i18n";
import {
  EcosystemPanel,
  type Section,
  type Row,
} from "@/components/EcosystemPanel";
import type { WorkspaceView } from "@/lib/api";
export async function EcosystemPage({
  params,
  searchParams,
  section,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ invite?: string; ref?: string }>;
  section: Section;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const query = await searchParams;
  const [view, team, ws] = await Promise.all([
    coreGet<Row>("/" + section),
    coreGet<Row>("/team"),
    coreGet<WorkspaceView>("/workspace"),
  ]);
  if (!ws.data || !team.data)
    return (
      <p role="alert">
        {locale === "en"
          ? "Workspace unavailable. Return to your personal workspace from the team menu."
          : "Workspace indisponible. Revenez à votre espace personnel depuis le menu équipe."}
      </p>
    );
  return (
    <EcosystemPanel
      section={section}
      initial={view.data ?? {}}
      team={team.data}
      entitlements={ws.data.entitlements}
      en={locale === "en"}
      invite={query.invite ?? query.ref ?? ""}
      status={view.status}
    />
  );
}
