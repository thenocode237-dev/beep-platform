import type { ReactNode } from "react";
import { notFound, redirect } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import type { Me } from "@/lib/api";
import { coreGet } from "@/lib/core";
import { getConsole, getDictionary, href, isLocale } from "@/lib/i18n";

export const dynamic = "force-dynamic";

export default async function AppLayout({ children, params }: { children: ReactNode; params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const me = await coreGet<Me>("/auth/me");
  if (!me.data) redirect(href(locale, "/login"));
  const enterprise=await coreGet<{contract?:{brandName?:string;brandColor?:string;status?:string;startsAt?:string;endsAt?:string}}>("/enterprise");
  const contract=enterprise.data?.contract;const active=contract?.status==="active"&&Date.parse(contract.startsAt??"")<=Date.now()&&Date.parse(contract.endsAt??"")>Date.now();
  return (
    <AppShell t={getConsole(locale)} locale={locale} email={me.data.email} brandName={active?contract?.brandName:undefined} brandColor={active?contract?.brandColor:undefined} logoutLabel={getDictionary(locale).app.logout}>
      {children}
    </AppShell>
  );
}
