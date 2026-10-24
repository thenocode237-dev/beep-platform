/**
 * Le signal Beep : un point émetteur et deux paires d'ondes symétriques.
 * Rayons 7,3 · 19,1 · 30,9 (rapport φ), trait 6,18 (10/φ), ouverture 360°/φ³.
 */
export function Logo({
  href = "/",
  label = "Beep",
}: {
  href?: string;
  label?: string;
}) {
  return (
    <a className="bp-logo" href={href} aria-label={label}>
      <svg
        className="bp-logo__mark"
        width="34"
        height="34"
        viewBox="14 14 72 72"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M64.10 37.11A19.1 19.1 0 0 1 64.10 62.89M35.90 62.89A19.1 19.1 0 0 1 35.90 37.11M72.81 29.15A30.9 30.9 0 0 1 72.81 70.85M27.19 70.85A30.9 30.9 0 0 1 27.19 29.15"
          stroke="currentColor"
          strokeWidth="6.18"
          strokeLinecap="round"
        />
        <circle cx="50" cy="50" r="7.3" fill="var(--color-brand-signature)" />
      </svg>
      <span aria-hidden="true">
        beep<span className="bp-logo__dot">.</span>
      </span>
    </a>
  );
}
