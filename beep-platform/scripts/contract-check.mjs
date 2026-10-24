// Test de contrat côté serveur : interroge le core RÉEL et valide chaque réponse contre les schémas du contrat OpenAPI.
// Usage : CORE_URL=http://core:8080 ADMIN_API_TOKEN=… node scripts/contract-check.mjs
import { readFileSync } from "node:fs";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { parse } from "yaml";

const spec = parse(readFileSync(new URL("../contracts/beep-admin.openapi.yaml", import.meta.url), "utf8"));
const base = `${process.env.CORE_URL ?? "http://core:8080"}/internal/admin/v1`;
const token = process.env.ADMIN_API_TOKEN;
if (!token) throw new Error("ADMIN_API_TOKEN requis");

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: "contract", components: spec.components });

const schemaOf = (ref) => ({ $ref: `contract#${ref.replace("#", "")}` });
let failures = 0;
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "OK  " : "FAIL"} ${name}${ok ? "" : ` — ${detail}`}`);
  if (!ok) failures++;
};

async function call(path, { auth = true, headers = {} } = {}) {
  const res = await fetch(`${base}${path}`, { headers: { ...(auth ? { Authorization: `Bearer ${token}` } : {}), ...headers } });
  const text = await res.text();
  let json; try { json = JSON.parse(text); } catch { /* corps non JSON */ }
  return { status: res.status, json, type: res.headers.get("content-type") ?? "" };
}

// Chaque opération GET décrite par le contrat : la réponse 200 doit respecter son schéma.
for (const [path, item] of Object.entries(spec.paths)) {
  const op = item.get;
  if (!op) continue;
  const okSchema = op.responses["200"].content["application/json"].schema;
  const r = await call(path.replace(/\{[^}]+\}/g, "00000000-0000-0000-0000-000000000000"));
  check(`GET ${path} -> 200`, r.status === 200, `statut ${r.status}`);
  const validate = ajv.compile(schemaOf(okSchema.$ref));
  check(`GET ${path} respecte le schéma`, validate(r.json), JSON.stringify(validate.errors));
}

// Sécurité décrite par le contrat : 401 problem+json sans jeton valide.
const anon = await call("/users", { auth: false });
check("GET /users sans jeton -> 401 problem+json", anon.status === 401 && anon.type.includes("problem+json") && anon.json?.code === "unauthorized", `statut ${anon.status} ${anon.type}`);
const badToken = await fetch(`${base}/users`, { headers: { Authorization: "Bearer not-the-token" } });
check("GET /users avec un mauvais jeton -> 401", badToken.status === 401, `statut ${badToken.status}`);

// Curseur falsifié : 400 problem+json avec code stable.
const bad = await call("/users?cursor=%21%21not-a-cursor");
check("curseur invalide -> 400 invalid_request", bad.status === 400 && bad.json?.code === "invalid_request", `statut ${bad.status} ${JSON.stringify(bad.json)}`);

// La version publiée par le core doit être celle du contrat.
const meta = await call("/meta");
check(`version du contrat = ${spec.info.version}`, meta.json?.contract === spec.info.version, `core: ${meta.json?.contract}`);

process.exit(failures ? 1 : 0);
