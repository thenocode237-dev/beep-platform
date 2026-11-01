/**
 * Le logo Beepclick : un point émetteur relié à trois destinations.
 */
export function Logo({
  href = "/",
  label = "Beepclick",
  variant = "full",
}: {
  href?: string;
  label?: string;
  variant?: "full" | "wordmark" | "mark";
}) {
  return (
    <a className="bp-logo" href={href} aria-label={label}>
      {variant !== "wordmark" && <svg
        className="bp-logo__mark"
        width={variant === "mark" ? 44 : 34}
        height={variant === "mark" ? 44 : 34}
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
      {variant !== "mark" && <span aria-hidden="true">
        Beepclick
      </span>}
    </a>
  );
}
