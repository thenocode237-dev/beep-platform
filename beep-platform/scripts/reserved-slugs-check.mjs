// Contrôle CI (specs §8.4) : chaque chemin routé vers beep-web par Caddy doit être un slug réservé côté core,
// sinon un utilisateur pourrait créer un lien que le site masquerait (ou l'inverse). Aucune dépendance.
// Usage : node scripts/reserved-slugs-check.mjs
import { readFileSync } from "node:fs";

const root = new URL("..", import.meta.url);
const java = readFileSync(new URL("beep/core/domain/src/main/java/io/beep/core/domain/link/ReservedSlugs.java", root), "utf8");

// Ensembles SYSTEM et PROTECTED : toutes les chaînes littérales des Set.of(...)
const reserved = new Set();
for (const block of java.matchAll(/Set<String> (?:SYSTEM|PROTECTED) = Set\.of\(([\s\S]*?)\);/g)) {
  for (const m of block[1].matchAll(/"([^"]+)"/g)) reserved.add(m[1].toLowerCase());
}
if (reserved.size < 20) throw new Error(`liste de slugs réservés introuvable ou incomplète (${reserved.size})`);

let failures = 0;
for (const file of ["infra/caddy/Caddyfile", "infra/caddy/Caddyfile.dev"]) {
  const caddy = readFileSync(new URL(file, root), "utf8");
  const line = caddy.split("\n").find((l) => /^\s*@web\s+path\s/.test(l));
  if (!line) {
    console.log(`FAIL ${file} : matcher @web introuvable`);
    failures++;
    continue;
  }
  const segments = new Set(
    line.trim().split(/\s+/).slice(2) // "@web path /a /b* …"
      .map((p) => p.replace(/^\//, "").split("/")[0].replace(/\*$/, ""))
      .filter((s) => s !== ""),
  );
  for (const s of segments) {
    const ok = reserved.has(s.toLowerCase());
    if (!ok) failures++;
    console.log(`${ok ? "OK  " : "FAIL"} ${file} : /${s}${ok ? "" : " n'est pas un slug réservé (ReservedSlugs.java)"}`);
  }
}
console.log(failures ? `\n${failures} chemin(s) non réservé(s)` : "\nToutes les routes du site sont des slugs réservés.");
process.exit(failures ? 1 : 0);
