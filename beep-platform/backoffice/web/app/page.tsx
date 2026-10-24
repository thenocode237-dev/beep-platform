import { redirect } from "next/navigation";
import { getMe } from "@/lib/api";
import { navItems as items } from "@/lib/nav";

export const dynamic = "force-dynamic";

/** Premier module autorisé pour ce rôle. */
export default async function Index() {
  const me = await getMe();
  if (!me.data) redirect("/login");
  const first = items.find((i) => me.data!.permissions?.includes(i.permission));
  redirect(first?.href ?? "/menu");
}
