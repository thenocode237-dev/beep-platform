import { getEnterprise, getMe } from "@/lib/api";
import { EnterpriseConsolePanel } from "@/components/EnterpriseConsolePanel";
export const dynamic = "force-dynamic";
export default async function Page() {
  const [view, me] = await Promise.all([getEnterprise(), getMe()]);
  return (
    <EnterpriseConsolePanel
      initial={view.data ?? {}}
      status={view.status}
      permissions={me.data?.permissions ?? []}
    />
  );
}
