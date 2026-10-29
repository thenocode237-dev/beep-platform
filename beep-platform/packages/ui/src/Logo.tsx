/**
 * Le logo BeepClick : un point émetteur relié à trois destinations.
 */
export function Logo({
  href = "/",
  label = "BeepClick",
  variant = "full",
}: {
  href?: string;
  label?: string;
  variant?: "full" | "wordmark";
}) {
  return (
    <a className="bp-logo" href={href} aria-label={label}>
      {variant === "full" && <svg
        className="bp-logo__mark"
        width="34"
        height="34"
        viewBox="192 90 700 700"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M329 440L543 280M329 440H669M329 440L543 600"
          stroke="currentColor"
          strokeWidth="46"
        />
        <circle cx="623" cy="220" r="92" stroke="currentColor" strokeWidth="46" />
        <circle cx="769" cy="440" r="92" stroke="currentColor" strokeWidth="46" />
        <circle cx="623" cy="660" r="92" stroke="currentColor" strokeWidth="46" />
        <circle cx="329" cy="440" r="128" fill="currentColor" />
      </svg>}
      <span aria-hidden="true">
        BeepClick
      </span>
    </a>
  );
}
