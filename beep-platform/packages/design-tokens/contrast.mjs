// Vérifie les contrastes WCAG des paires texte/fond de la couche sémantique (échoue si < seuil).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const t = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "tokens.json"), "utf8"));
const get = (path) => path.split(".").reduce((o, k) => o?.[k], t.primitive);
const hex = (v) => {
  const m = /^\{(.+)\}$/.exec(v);
  return m ? get(m[1]) : v;
};
const lum = (h) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

// [texte, fond, seuil]
const pairs = [
  ["color-text-primary", "color-bg-page", 4.5],
  ["color-text-primary", "color-bg-surface", 4.5],
  ["color-text-secondary", "color-bg-page", 4.5],
  ["color-text-secondary", "color-bg-surface", 4.5],
  ["color-text-muted", "color-bg-surface", 4.5],
  ["color-text-link", "color-bg-surface", 4.5],
  ["color-action-primary-text", "color-action-primary-bg", 4.5],
  ["color-action-primary-text", "color-action-primary-bg-hover", 4.5],
  ["color-action-accent-text", "color-action-accent-bg", 4.5],
  ["color-border-strong", "color-bg-surface", 3],
  ["color-focus-ring", "color-bg-surface", 3],
  ["color-on-blue", "color-surface-blue", 4.5],
  ["color-text-secondary", "color-surface-mist", 4.5],
  ["color-brand-signature", "color-bg-page", 4.5],
  ["color-scene-paper", "color-scene-navy", 4.5],
  ["color-scene-mist", "color-scene-navy", 4.5],
  ["color-scene-ink", "color-scene-sky", 4.5],
];

let failed = 0;
for (const theme of ["light", "dark"]) {
  const s = t.semantic[theme];
  for (const [fg, bg, min] of pairs) {
    const r = ratio(hex(s[fg]), hex(s[bg]));
    const ok = r >= min;
    if (!ok) failed++;
    console.log(`${ok ? "OK  " : "FAIL"} [${theme}] ${fg} / ${bg}: ${r.toFixed(2)} (min ${min})`);
  }
}
process.exit(failed ? 1 : 0);
