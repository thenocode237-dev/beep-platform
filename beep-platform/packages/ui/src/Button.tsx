import type { ButtonHTMLAttributes, ReactNode } from "react";

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "accent" | "secondary" | "ghost";
  size?: "md" | "sm";
  block?: boolean;
  loading?: boolean;
  children: ReactNode;
};

/** Bouton : cible tactile ≥ 44 px, désactivé pendant l'appel asynchrone (`loading`). */
export function Button({ variant = "primary", size = "md", block, loading, className, children, disabled, ...rest }: ButtonProps) {
  const cls = ["bp-btn", variant !== "primary" && `bp-btn--${variant}`, size === "sm" && "bp-btn--sm", block && "bp-btn--block", className]
    .filter(Boolean)
    .join(" ");
  return (
    <button className={cls} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="bp-spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}
