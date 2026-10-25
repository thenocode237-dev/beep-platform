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
const ZERO = "00000000-0000-0000-0000-000000000000";
const STAFF = "11111111-1111-1111-1111-111111111111";

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: "contract", components: spec.components });

const validator = (ref) => ajv.compile({ $ref: `contract#${ref.replace("#", "")}` });
let failures = 0;
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "OK  " : "FAIL"} ${name}${ok ? "" : ` — ${detail}`}`);
  if (!ok) failures++;
};

async function call(method, path, { auth = true, body } = {}) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: { ...(auth ? { Authorization: `Bearer ${token}` } : {}), "X-Staff-Id": STAFF, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json; try { json = JSON.parse(text); } catch { /* corps non JSON */ }
  return { status: res.status, json, type: res.headers.get("content-type") ?? "" };
}

const schemaOf = (op, status) => {
  const content = op.responses[status]?.content;
  return content?.["application/json"]?.schema ?? content?.["application/problem+json"]?.schema;
};

// 1. Chaque GET décrit par le contrat. Sans paramètre de chemin : 200 + schéma. Avec : identifiant inconnu -> 404 problem+json.
for (const [path, item] of Object.entries(spec.paths)) {
  const op = item.get;
  if (!op) continue;
  if (path.includes("{")) {
    const r = await call("GET", path.replace(/\{[^}]+\}/g, ZERO));
    check(`GET ${path} (inconnu) -> 404 not_found`, r.status === 404 && r.type.includes("problem+json") && r.json?.code === "not_found", `statut ${r.status} ${JSON.stringify(r.json)}`);
    continue;
  }
  const r = await call("GET", path);
  check(`GET ${path} -> 200`, r.status === 200, `statut ${r.status}`);
  const schema = schemaOf(op, "200");
  const validate = schema.$ref ? validator(schema.$ref) : ajv.compile(schema);
  check(`GET ${path} respecte le schéma`, validate(r.json), JSON.stringify(validate.errors));
}

// 2. Paramètres de recherche ajoutés en 0.2.0
const search = await call("GET", "/users?q=example&limit=5");
check("GET /users?q= -> 200 + schéma UsersPage", search.status === 200 && validator("#/components/schemas/UsersPage")(search.json), `statut ${search.status}`);
const links = await call("GET", "/links?q=a&limit=5");
check("GET /links?q= -> 200 + schéma LinksPage", links.status === 200 && validator("#/components/schemas/LinksPage")(links.json), `statut ${links.status}`);

// 3. Écritures : invitation (201), validation (400), ressources inconnues (404)
const email = `contract-${Date.now()}@example.com`;
const inv = await call("POST", "/invitations", { body: { email, locale: "fr" } });
const vInv = validator("#/components/schemas/Invitation");
check("POST /invitations -> 201 + schéma Invitation", inv.status === 201 && vInv(inv.json) && inv.json.invitedBy === STAFF, `statut ${inv.status} ${JSON.stringify(vInv.errors ?? inv.json)}`);
const badInv = await call("POST", "/invitations", { body: { email: "pas-une-adresse" } });
check("POST /invitations (adresse invalide) -> 400 invalid_email", badInv.status === 400 && badInv.json?.code === "invalid_email", `statut ${badInv.status}`);
const ent = await call("PUT", `/workspaces/${ZERO}/entitlements`, { body: { overrides: { "links.stock_max": 10 }, reason: "test de contrat" } });
check("PUT /workspaces/{id}/entitlements (inconnu) -> 404", ent.status === 404, `statut ${ent.status}`);
const entBad = await call("PUT", `/workspaces/${ZERO}/entitlements`, { body: { overrides: { "inconnu.cle": 1 }, reason: "test de contrat" } });
check("PUT /workspaces/{id}/entitlements (clé inconnue) -> 400 unknown_entitlement", entBad.status === 400 && entBad.json?.code === "unknown_entitlement", `statut ${entBad.status}`);
const dis = await call("POST", `/links/${ZERO}/disable`, { body: { reason: "test de contrat" } });
check("POST /links/{id}/disable (inconnu) -> 404", dis.status === 404, `statut ${dis.status}`);
const st = await call("PATCH", `/users/${ZERO}/status`, { body: { status: "suspended", reason: "test de contrat" } });
check("PATCH /users/{id}/status (inconnu) -> 404", st.status === 404, `statut ${st.status}`);

// 4. Sécurité décrite par le contrat : 401 problem+json sans jeton valide.
const anon = await call("GET", "/users", { auth: false });
check("GET /users sans jeton -> 401 problem+json", anon.status === 401 && anon.type.includes("problem+json") && anon.json?.code === "unauthorized", `statut ${anon.status} ${anon.type}`);
const badToken = await fetch(`${base}/users`, { headers: { Authorization: "Bearer not-the-token" } });
check("GET /users avec un mauvais jeton -> 401", badToken.status === 401, `statut ${badToken.status}`);

// 5. Curseur falsifié : 400 problem+json avec code stable.
const bad = await call("GET", "/users?cursor=%21%21not-a-cursor");
check("curseur invalide -> 400 invalid_request", bad.status === 400 && bad.json?.code === "invalid_request", `statut ${bad.status} ${JSON.stringify(bad.json)}`);

// 6. La version publiée par le core doit être celle du contrat.
const meta = await call("GET", "/meta");
check(`version du contrat = ${spec.info.version}`, meta.json?.contract === spec.info.version, `core: ${meta.json?.contract}`);

process.exit(failures ? 1 : 0);
