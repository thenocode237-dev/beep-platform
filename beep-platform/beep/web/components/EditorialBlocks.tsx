/** Text-only format: React escapes the body, including any pasted HTML. */
export function EditorialBlocks({ body }: { body: string }) {
  return (
    <div className="public-prose bp-stack">
      {body
        .split(/\n\s*\n/)
        .filter(Boolean)
        .map((block, i) =>
          block.startsWith("## ") ? (
            <h2 key={i}>{block.slice(3)}</h2>
          ) : block.startsWith("# ") ? (
            <h2 key={i}>{block.slice(2)}</h2>
          ) : (
            <p className="eco-prewrap" key={i}>
              {block}
            </p>
          ),
        )}
    </div>
  );
}
