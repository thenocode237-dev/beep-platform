import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { Shell } from "@/components/Shell";
import { getMe, getStatus } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function ConsoleLayout({ children }: { children: ReactNode }) {
  const [me, status] = await Promise.all([getMe(), getStatus()]);
  if (!me.data) redirect("/login");
  return (
    <Shell email={me.data.email} role={me.data.role} permissions={me.data.permissions ?? []} coreUp={status.data?.core === "up"}>
      {children}
    </Shell>
  );
}
