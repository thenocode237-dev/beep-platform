// Génère dist/tokens.css et TOKENS.md à partir de tokens.json. Aucune dépendance.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const tokens = JSON.parse(readFileSync(join(here, "tokens.json"), "utf8"));

const flatten = (obj, prefix = []) =>
  Object.entries(obj).flatMap(([k, v]) =>
    typeof v === "object" && v !== null ? flatten(v, [...prefix, k]) : [[[...prefix, k].join("-"), v]],
  );

// {color.ink.50} -> var(--color-ink-50) ; {semantic.x} -> var(--x)
const resolve = (value) =>
  String(value).replace(/\{([^}]+)\}/g, (_, path) => {
    const parts = path.split(".");
    return parts[0] === "semantic" ? `var(--${parts.slice(1).join("-")})` : `var(--${parts.join("-")})`;
  });

const decls = (entries, indent = "  ") => entries.map(([k, v]) => `${indent}--${k}: ${resolve(v)};`).join("\n");

const primitive = flatten(tokens.primitive);
const light = flatten(tokens.semantic.light);
const dark = flatten(tokens.semantic.dark);
const statics = flatten(tokens.semantic.static);
const component = flatten(tokens.component);
const breakpoints = flatten(tokens.breakpoints).map(([k, v]) => [`bp-${k}`, v]);

const css = `/* GÉNÉRÉ par build.mjs depuis tokens.json — ne pas modifier à la main. */
:root {
  /* --- Primitives --- */
${decls(primitive)}
  /* --- Sémantique (statique) --- */
${decls(statics)}
  /* --- Composants --- */
${decls(component)}
  /* --- Breakpoints (référence : les media queries ne lisent pas les variables) --- */
${decls(breakpoints)}
  color-scheme: light;
}

/* --- Sémantique : thème clair (défaut) --- */
:root,
:root[data-theme="light"] {
${decls(light)}
}

/* --- Sémantique : thème sombre (préférence système, sauf si le clair est forcé) --- */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    color-scheme: dark;
${decls(dark, "    ")}
  }
}

/* --- Sémantique : thème sombre forcé --- */
:root[data-theme="dark"] {
  color-scheme: dark;
${decls(dark)}
}
`;

mkdirSync(join(here, "dist"), { recursive: true });
writeFileSync(join(here, "dist", "tokens.css"), css);

const rows = (entries) => entries.map(([k, v]) => `| \`--${k}\` | \`${String(v)}\` |`).join("\n");
const md = `# Design tokens Beep

> Généré par \`packages/design-tokens/build.mjs\`. Source : \`tokens.json\`. Utilisation : \`@import "@beep/design-tokens/tokens.css"\`.

## Règles
1. Les couleurs passent par des tokens **sémantiques** (\`--color-bg-surface\`). Les composants partagés consomment leurs tokens (\`--button-height\`) ; les dimensions propres aux illustrations restent dans leur CSS.
2. Les tokens **primitifs** ne servent qu'à définir la couche sémantique.
3. Clair et sombre sont conçus ensemble : le thème suit \`prefers-color-scheme\`, ou \`data-theme="light|dark"\` sur \`<html>\`.
4. Mobile-first : cibles tactiles ≥ \`--touch-target-min\` (44 px), corps de texte 16 px, échelle de titres fluide (\`clamp\`).
5. Breakpoints : 375 / 768 / 1024 / 1440 px (\`min-width\`, mobile d'abord).
6. Mouvement : \`--duration-*\` + \`--easing-*\`, neutralisé par \`prefers-reduced-motion\`.

## Primitifs
| Token | Valeur |
|---|---|
${rows(primitive)}

## Sémantique — clair
| Token | Valeur |
|---|---|
${rows(light)}

## Sémantique — sombre
| Token | Valeur |
|---|---|
${rows(dark)}

## Sémantique — statique (typo, gabarits, tactile)
| Token | Valeur |
|---|---|
${rows(statics)}

## Composants
| Token | Valeur |
|---|---|
${rows(component)}

## Breakpoints
| Token | Valeur |
|---|---|
${rows(breakpoints)}
`;
writeFileSync(join(here, "TOKENS.md"), md);
console.log(`tokens: ${primitive.length} primitifs, ${light.length} sémantiques, ${component.length} composants`);
