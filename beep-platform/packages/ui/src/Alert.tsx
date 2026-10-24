import type { ReactNode } from "react";

/** Message de retour : `danger` annoncé immédiatement (role=alert), les autres poliment (role=status). */
export function Alert({ tone = "info", children }: { tone?: "danger" | "success" | "info"; children: ReactNode }) {
  return (
    <div className={`bp-alert bp-alert--${tone}`} role={tone === "danger" ? "alert" : "status"}>
      {children}
    </div>
  );
}
