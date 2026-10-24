import type { ReactNode } from "react";

export function Badge({ soon, children }: { soon?: boolean; children: ReactNode }) {
  return <span className={soon ? "bp-badge bp-badge--soon" : "bp-badge"}>{children}</span>;
}
