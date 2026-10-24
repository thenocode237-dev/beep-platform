// Bout en bout sur la stack Compose en cours d'exécution (make up). Aucune dépendance : Node ≥ 20.
//   Parcours utilisateur : inscription -> e-mail (Mailpit) -> confirmation -> connexion -> TOTP -> réinitialisation
//   Parcours staff       : connexion (TOTP obligatoire) -> l'utilisateur inscrit apparaît -> audit append-only
import { createHmac } from "node:crypto";
import http from "node:http";
import { execFileSync } from "node:child_process";

const HTTP = process.env.E2E_HTTP ?? "http://127.0.0.1";
const SITE = process.env.E2E_SITE_HOST ?? "beep.localhost";
const BO = process.env.E2E_BO_HOST ?? "dashboard.beep.localhost";
const MAIL = process.env.E2E_MAIL_HOST ?? "mail.beep.localhost";
const STAFF_EMAIL = process.env.E2E_STAFF_EMAIL;
const STAFF_PASSWORD = process.env.E2E_STAFF_PASSWORD ?? "staff-password-1234";
const STAFF_TOTP_SECRET = process.env.E2E_STAFF_TOTP_SECRET;

let failures = 0;
const step = (name, ok, detail = "") => {
  console.log(`${ok ? "OK  " : "FAIL"} ${name}${ok ? "" : ` — ${detail}`}`);
  if (!ok) failures++;
};

// ---- client HTTP minimal avec pot de cookies, adressage par en-tête Host (pas besoin de DNS *.localhost) ----
// node:http plutôt que fetch : fetch ignore l'en-tête Host, indispensable pour atteindre les sites virtuels de Caddy.
class Client {
  jar = new Map();
  constructor(host) { this.host = host; }
  request(method, path, body) {
    const url = new URL(HTTP);
    const payload = body === undefined ? undefined : JSON.stringify(body);
    return new Promise((resolve, reject) => {
      const req = http.request({
        hostname: url.hostname,
        port: url.port || 80,
        path,
        method,
        headers: {
          host: this.host,
          cookie: [...this.jar].map(([k, v]) => `${k}=${v}`).join("; "),
          ...(payload !== undefined || method === "POST" ? { "content-type": "application/json" } : {}),
          ...(payload !== undefined ? { "content-length": Buffer.byteLength(payload) } : {}),
        },
      }, (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const setCookies = res.headers["set-cookie"] ?? [];
          for (const c of setCookies) {
            const [pair, ...attrs] = c.split(";");
            const [name, ...v] = pair.split("=");
            const value = v.join("=");
            if (/max-age=0/i.test(attrs.join(";")) || value === "") this.jar.delete(name.trim());
            else this.jar.set(name.trim(), value);
          }
          const text = Buffer.concat(chunks).toString("utf8");
          let json; try { json = JSON.parse(text); } catch { /* HTML ou vide */ }
          const headers = { get: (n) => { const h = res.headers[n.toLowerCase()]; return Array.isArray(h) ? h.join(", ") : h ?? null; }, getSetCookie: () => setCookies };
          resolve({ status: res.statusCode, json, text, headers });
        });
      });
      req.on("error", reject);
      if (payload !== undefined) req.write(payload);
      req.end();
    });
  }
  get = (p) => this.request("GET", p);
  post = (p, b) => this.request("POST", p, b);
}

// ---- TOTP RFC 6238 (SHA1, 6 chiffres, 30 s) ----
function base32(s) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of s.replace(/=+$/, "").toUpperCase()) bits += A.indexOf(c).toString(2).padStart(5, "0");
  return Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
}
function totp(secret, atMs = Date.now()) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(atMs / 30000)));
  const h = createHmac("sha1", base32(secret)).update(counter).digest();
  const o = h[h.length - 1] & 0xf;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1e6)).padStart(6, "0");
}

const site = new Client(SITE);
const bo = new Client(BO);
const mail = new Client(MAIL);

async function lastMailTokenFor(email, kind) {
  for (let i = 0; i < 20; i++) {
    const list = await mail.get("/api/v1/search?query=" + encodeURIComponent(`to:${email}`));
    const msg = (list.json?.messages ?? []).find((m) => (kind === "verify" ? /confirm|bienvenue|welcome/i.test(m.Subject) : /reset|réinitial/i.test(m.Subject)));
    if (msg) {
      const full = await mail.get(`/api/v1/message/${msg.ID}`);
      const m = /token=([A-Za-z0-9_-]{20,})/.exec(full.json?.Text ?? "");
      if (m) return m[1];
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return undefined;
}

const email = `e2e-${Date.now()}@example.com`;
const password = "correct-horse-battery";

// ===== Site public =====
let r = await site.get("/");
step("site : accueil FR (200, lang=fr, viewport mobile)", r.status === 200 && /<html lang="fr"/.test(r.text) && /width=device-width/.test(r.text), `statut ${r.status}`);
r = await site.get("/en");
step("site : accueil EN (200, lang=en)", r.status === 200 && /<html lang="en"/.test(r.text), `statut ${r.status}`);
r = await site.get("/fr/login");
step("site : /fr/login redirige vers l'URL canonique /login", r.status === 308 && r.headers.get("location")?.endsWith("/login"), `statut ${r.status}`);
for (const p of ["/login", "/signup", "/forgot-password", "/en/login", "/en/signup"]) {
  r = await site.get(p);
  step(`site : ${p} (200)`, r.status === 200, `statut ${r.status}`);
}
r = await site.get("/app");
step("site : /app sans session redirige vers la connexion", r.status === 307 && /login/.test(r.headers.get("location") ?? ""), `statut ${r.status}`);
r = await site.get("/internal/admin/v1/users");
step("sécurité : /internal/* jamais exposé (404)", r.status === 404, `statut ${r.status}`);
r = await site.get("/actuator/health");
step("sécurité : /actuator/* jamais exposé (404)", r.status === 404, `statut ${r.status}`);

// ===== Inscription =====
r = await site.post("/v1/auth/signup", { email, password: "short", locale: "fr" });
step("inscription : mot de passe trop court -> 400 weak_password", r.status === 400 && r.json?.code === "weak_password", `statut ${r.status} ${r.text}`);
r = await site.post("/v1/auth/signup", { email, password, locale: "fr" });
step("inscription : 202", r.status === 202, `statut ${r.status} ${r.text}`);
r = await site.post("/v1/auth/signup", { email, password, locale: "fr" });
step("inscription : adresse déjà connue -> même réponse 202 (pas d'énumération)", r.status === 202, `statut ${r.status}`);

r = await site.post("/v1/auth/login", { email, password });
step("connexion avant confirmation -> 403 email_not_verified", r.status === 403 && r.json?.code === "email_not_verified", `statut ${r.status} ${r.text}`);

const verifyToken = await lastMailTokenFor(email, "verify");
step("e-mail de confirmation reçu (Mailpit)", !!verifyToken);
r = await site.post("/v1/auth/verify-email", { token: "forged-token" });
step("confirmation : faux jeton -> 400 invalid_token", r.status === 400 && r.json?.code === "invalid_token", `statut ${r.status}`);
r = await site.post("/v1/auth/verify-email", { token: verifyToken });
step("confirmation : 204", r.status === 204, `statut ${r.status}`);
r = await site.post("/v1/auth/verify-email", { token: verifyToken });
step("confirmation : le jeton ne sert qu'une fois", r.status === 400, `statut ${r.status}`);

// ===== Connexion, session, TOTP =====
r = await site.post("/v1/auth/login", { email, password: "wrong-password-123" });
step("connexion : mauvais mot de passe -> 401 invalid_credentials", r.status === 401 && r.json?.code === "invalid_credentials", `statut ${r.status}`);
r = await site.post("/v1/auth/login", { email, password });
const cookie = [...site.jar.keys()].find((k) => /beep_session$/.test(k));
step("connexion : 200 + cookie de session", r.status === 200 && !!cookie, `statut ${r.status}`);
const setCookie = r.headers.getSetCookie?.().join(" ") ?? "";
step("cookie : HttpOnly, SameSite=Lax, sans attribut Domain", /HttpOnly/i.test(setCookie) && /SameSite=Lax/i.test(setCookie) && !/Domain=/i.test(setCookie), setCookie);
r = await site.get("/v1/auth/me");
step("/me : l'utilisateur connecté", r.status === 200 && r.json?.email === email && r.json?.emailVerified === true, `statut ${r.status} ${r.text}`);
r = await site.get("/app");
step("espace client : page /app rendue côté serveur (200, e-mail affiché)", r.status === 200 && r.text.includes(email), `statut ${r.status}`);

r = await site.post("/v1/auth/totp/setup");
const secret = r.json?.secret;
step("TOTP : génération du secret", r.status === 200 && /^[A-Z2-7]{32}$/.test(secret ?? ""), `statut ${r.status} ${r.text}`);
r = await site.post("/v1/auth/totp/enable", { code: "000000" });
step("TOTP : mauvais code -> 401 invalid_totp", r.status === 401 && r.json?.code === "invalid_totp", `statut ${r.status}`);
r = await site.post("/v1/auth/totp/enable", { code: totp(secret) });
step("TOTP : activation 204", r.status === 204, `statut ${r.status} ${r.text}`);
await site.post("/v1/auth/logout");
r = await site.get("/v1/auth/me");
step("déconnexion : la session est révoquée (401)", r.status === 401, `statut ${r.status}`);
r = await site.post("/v1/auth/login", { email, password });
step("connexion avec TOTP actif, sans code -> 401 totp_required", r.status === 401 && r.json?.code === "totp_required", `statut ${r.status}`);
r = await site.post("/v1/auth/login", { email, password, totp: totp(secret) });
step("connexion avec code TOTP -> 200", r.status === 200, `statut ${r.status} ${r.text}`);

// ===== Réinitialisation du mot de passe =====
const oldSession = new Map(site.jar);
r = await site.post("/v1/auth/password/forgot", { email: "ghost@example.com" });
step("réinitialisation : adresse inconnue -> même réponse 202", r.status === 202, `statut ${r.status}`);
r = await site.post("/v1/auth/password/forgot", { email, locale: "fr" });
step("réinitialisation : 202", r.status === 202, `statut ${r.status}`);
const resetToken = await lastMailTokenFor(email, "reset");
step("e-mail de réinitialisation reçu", !!resetToken);
const newPassword = "an-even-better-passphrase";
r = await site.post("/v1/auth/password/reset", { token: resetToken, password: newPassword });
step("réinitialisation : 204", r.status === 204, `statut ${r.status} ${r.text}`);
const stale = new Client(SITE);
stale.jar = oldSession;
r = await stale.get("/v1/auth/me");
step("réinitialisation : les anciennes sessions sont révoquées", r.status === 401, `statut ${r.status}`);
r = await new Client(SITE).post("/v1/auth/login", { email, password: newPassword, totp: totp(secret) });
step("connexion avec le nouveau mot de passe -> 200", r.status === 200, `statut ${r.status} ${r.text}`);

// ===== Back office =====
if (!STAFF_EMAIL || !STAFF_TOTP_SECRET) {
  console.log("SKIP back office : définir E2E_STAFF_EMAIL et E2E_STAFF_TOTP_SECRET (scripts/e2e.sh s'en charge)");
} else {
  r = await bo.get("/api/users");
  step("back office : /api/users sans session -> 401", r.status === 401, `statut ${r.status}`);
  r = await bo.post("/api/auth/login", { email: STAFF_EMAIL, password: STAFF_PASSWORD, totp: "000000" });
  step("back office : TOTP incorrect -> 401 invalid_credentials", r.status === 401 && r.json?.code === "invalid_credentials", `statut ${r.status}`);
  r = await bo.post("/api/auth/login", { email: STAFF_EMAIL, password: STAFF_PASSWORD });
  step("back office : TOTP absent -> 401 (obligatoire)", r.status === 401, `statut ${r.status}`);
  r = await bo.post("/api/auth/login", { email: STAFF_EMAIL, password: STAFF_PASSWORD, totp: totp(STAFF_TOTP_SECRET) });
  const sc = r.headers.getSetCookie?.().join(" ") ?? "";
  step("back office : connexion 200 + cookie HttpOnly SameSite=Strict", r.status === 200 && /HttpOnly/i.test(sc) && /SameSite=Strict/i.test(sc), `statut ${r.status} ${sc}`);
  r = await bo.get("/api/status");
  step("back office : le core est joignable (mode nominal)", r.status === 200 && r.json?.core === "up", `statut ${r.status} ${r.text}`);
  r = await bo.get("/api/users?limit=100");
  step("back office : l'utilisateur inscrit sur le site apparaît (via l'API admin du core)", r.status === 200 && r.json?.items?.some((u) => u.email === email && u.emailVerified && u.totpEnabled), `statut ${r.status} ${r.text.slice(0, 200)}`);
  r = await bo.get("/users");
  step("back office : page « Utilisateurs » rendue côté serveur", r.status === 200 && r.text.includes(email), `statut ${r.status}`);
  step("back office : sécurité des en-têtes (CSP, noindex)", !!r.headers.get("content-security-policy") && /noindex/i.test(r.headers.get("x-robots-tag") ?? ""), "en-têtes manquants");

  // Journal d'audit : écrit pour la connexion et la consultation, et inaltérable (même pour le superuser).
  const sql = (q) => execFileSync("docker", ["compose", "-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "exec", "-T", "postgres", "sh", "-c", `psql -U "$POSTGRES_USER" -d backoffice -tAc "${q}"`], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
  try {
    const n = Number(sql(`select count(*) from audit_log where actor_email = '${STAFF_EMAIL}' and action in ('staff.login','users.list')`));
    step("audit : connexion et consultation journalisées", n >= 2, `entrées: ${n}`);
    const failed = Number(sql(`select count(*) from audit_log where action = 'staff.login_failed' and actor_email = '${STAFF_EMAIL}'`));
    step("audit : les échecs de connexion sont journalisés", failed >= 2, `entrées: ${failed}`);
    let blocked = false;
    try { sql("update audit_log set action = 'forged'"); } catch (e) { blocked = /ajout seul/.test(String(e.stderr)); }
    step("audit : UPDATE refusé par la base (append-only)", blocked);
  } catch (e) {
    step("audit : accès à la base", false, String(e.message).slice(0, 200));
  }

  await bo.post("/api/auth/logout");
  r = await bo.get("/api/auth/me");
  step("back office : déconnexion -> 401", r.status === 401, `statut ${r.status}`);
}

console.log(failures ? `\n${failures} échec(s)` : "\nTous les contrôles passent.");
process.exit(failures ? 1 : 0);
