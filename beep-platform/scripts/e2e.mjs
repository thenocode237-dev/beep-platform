// Bout en bout sur la stack Compose en cours d'exécution (make up). Aucune dépendance : Node ≥ 20.
//   Phase 0 : inscription -> e-mail (Mailpit) -> confirmation -> connexion -> TOTP -> réinitialisation ; staff (TOTP), audit append-only
//   Phase 1 : bêta sur invitation, liens (slugs, quotas), redirection, QR dynamiques, page bio, statistiques, lien magique,
//             actions staff (désactivation, override, suspension) et leur journalisation
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
const BROWSER = "Mozilla/5.0 (Linux; Android 13; SM-A145F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

if (!STAFF_EMAIL || !STAFF_TOTP_SECRET) {
  console.error("Définir E2E_STAFF_EMAIL et E2E_STAFF_TOTP_SECRET (scripts/e2e.sh s'en charge) : la bêta est sur invitation.");
  process.exit(2);
}

let failures = 0;
const step = (name, ok, detail = "") => {
  console.log(`${ok ? "OK  " : "FAIL"} ${name}${ok ? "" : ` — ${detail}`}`);
  if (!ok) failures++;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- client HTTP minimal avec pot de cookies, adressage par en-tête Host (pas besoin de DNS *.localhost) ----
// node:http plutôt que fetch : fetch ignore l'en-tête Host, indispensable pour atteindre les sites virtuels de Caddy.
class Client {
  jar = new Map();
  constructor(host, ua) { this.host = host; this.ua = ua; }
  request(method, path, body, extra = {}) {
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
          ...(this.ua ? { "user-agent": this.ua } : {}),
          ...(payload !== undefined || ["POST", "PUT", "PATCH"].includes(method) ? { "content-type": "application/json" } : {}),
          ...(payload !== undefined ? { "content-length": Buffer.byteLength(payload) } : {}),
          ...extra,
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
          const raw = Buffer.concat(chunks);
          const text = raw.toString("utf8");
          let json; try { json = JSON.parse(text); } catch { /* HTML ou vide */ }
          const headers = { get: (n) => { const h = res.headers[n.toLowerCase()]; return Array.isArray(h) ? h.join(", ") : h ?? null; }, getSetCookie: () => setCookies };
          resolve({ status: res.statusCode, json, text, raw, headers });
        });
      });
      req.on("error", reject);
      if (payload !== undefined) req.write(payload);
      req.end();
    });
  }
  get = (p, extra) => this.request("GET", p, undefined, extra);
  head = (p) => this.request("HEAD", p);
  post = (p, b) => this.request("POST", p, b);
  put = (p, b) => this.request("PUT", p, b);
  patch = (p, b) => this.request("PATCH", p, b);
  del = (p) => this.request("DELETE", p);
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

const site = new Client(SITE, BROWSER);
const bo = new Client(BO);
const mail = new Client(MAIL);
const visitor = new Client(SITE, BROWSER); // visiteur anonyme d'un lien court

async function lastMailFor(email, subject) {
  for (let i = 0; i < 20; i++) {
    const list = await mail.get("/api/v1/search?query=" + encodeURIComponent(`to:${email}`));
    const msg = (list.json?.messages ?? []).find((m) => subject.test(m.Subject));
    if (msg) return (await mail.get(`/api/v1/message/${msg.ID}`)).json;
    await sleep(500);
  }
  return undefined;
}
async function lastMailTokenFor(email, kind) {
  const subject = { verify: /confirm|bienvenue|welcome/i, reset: /reset|réinitial/i, magic: /connexion|sign-in/i }[kind];
  const m = /token=([A-Za-z0-9_-]{20,})/.exec((await lastMailFor(email, subject))?.Text ?? "");
  return m?.[1];
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
for (const p of ["/login", "/signup", "/forgot-password", "/en/login", "/en/signup", "/login/magic", "/login/verify"]) {
  r = await site.get(p);
  step(`site : ${p} (200)`, r.status === 200, `statut ${r.status}`);
}
r = await site.get("/app");
step("site : /app sans session redirige vers la connexion", r.status === 307 && /login/.test(r.headers.get("location") ?? ""), `statut ${r.status}`);
r = await site.get("/internal/admin/v1/users");
step("sécurité : /internal/* jamais exposé (404)", r.status === 404, `statut ${r.status}`);
r = await site.get("/actuator/health");
step("sécurité : /actuator/* jamais exposé (404)", r.status === 404, `statut ${r.status}`);
r = await visitor.get("/ce-slug-n-existe-pas");
step("redirection : slug inconnu -> page 404 HTML", r.status === 404 && /text\/html/.test(r.headers.get("content-type") ?? "") && /introuvable/i.test(r.text), `statut ${r.status}`);

// ===== Back office : connexion (TOTP obligatoire) =====
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
r = await bo.get("/api/auth/me");
step("back office : /me expose les droits du rôle", r.status === 200 && r.json?.permissions?.includes("LINKS_MODERATE"), `statut ${r.status} ${r.text}`);

// ===== Bêta privée : inscription sur invitation =====
r = await site.post("/v1/auth/signup", { email, password, locale: "fr" });
step("bêta : inscription d'une adresse non invitée -> même réponse 202", r.status === 202, `statut ${r.status}`);
step("bêta : … et e-mail « invitation requise », aucun compte créé", !!(await lastMailFor(email, /bêta privée|private beta/i)), "e-mail absent");
r = await site.post("/v1/auth/login", { email, password });
step("bêta : pas de compte pour une adresse non invitée", r.status === 401, `statut ${r.status}`);
r = await bo.post("/api/invitations", { email, locale: "fr" });
step("back office : invitation envoyée (201)", r.status === 201 && r.json?.status === "pending", `statut ${r.status} ${r.text}`);
step("bêta : e-mail d'invitation reçu", !!(await lastMailFor(email, /invité|invited/i)), "e-mail absent");

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

// ===== Connexion, session =====
r = await site.post("/v1/auth/login", { email, password: "wrong-password-123" });
step("connexion : mauvais mot de passe -> 401 invalid_credentials", r.status === 401 && r.json?.code === "invalid_credentials", `statut ${r.status}`);
r = await site.post("/v1/auth/login", { email, password });
const cookie = [...site.jar.keys()].find((k) => /beep_session$/.test(k));
step("connexion : 200 + cookie de session", r.status === 200 && !!cookie, `statut ${r.status}`);
const setCookie = r.headers.getSetCookie?.().join(" ") ?? "";
step("cookie : HttpOnly, SameSite=Lax, sans attribut Domain", /HttpOnly/i.test(setCookie) && /SameSite=Lax/i.test(setCookie) && !/Domain=/i.test(setCookie), setCookie);
r = await site.get("/v1/auth/me");
step("/me : l'utilisateur connecté", r.status === 200 && r.json?.email === email && r.json?.emailVerified === true, `statut ${r.status} ${r.text}`);

// ===== Phase 1 : workspace, liens, quotas =====
r = await site.get("/v1/workspace");
const starter = r.json?.quotas?.find((q) => q.resource === "links");
step("workspace : plan Starter, 5 liens en stock", r.status === 200 && r.json?.plan?.code === "starter" && starter?.limit === 5 && starter?.period === "stock", `statut ${r.status} ${r.text}`);
const slug = `e2e${Date.now().toString(36)}`;
r = await site.post("/v1/links", { destination: "example.com/produit?id=42", slug, title: "Lien e2e" });
const link = r.json;
step("liens : création en un champ (201), https ajouté", r.status === 201 && link?.destination === "https://example.com/produit?id=42" && link?.shortUrl?.endsWith(`/${slug}`), `statut ${r.status} ${r.text}`);
r = await site.post("/v1/links", { destination: "https://example.com", slug });
step("liens : slug déjà pris -> 409 slug_taken", r.status === 409 && r.json?.code === "slug_taken", `statut ${r.status}`);
r = await site.post("/v1/links", { destination: "https://example.com", slug: "pricing" });
step("liens : slug réservé -> 400 reserved_slug", r.status === 400 && r.json?.code === "reserved_slug", `statut ${r.status}`);
r = await site.post("/v1/links", { destination: "javascript:alert(1)" });
step("liens : destination non http(s) -> 400 invalid_destination", r.status === 400 && r.json?.code === "invalid_destination", `statut ${r.status}`);
const extra = [];
for (let i = 0; i < 4; i++) extra.push((await site.post("/v1/links", { destination: `https://example.com/${i}` })).json);
step("liens : slugs générés en base62 sur 7 caractères", extra.every((l) => /^[0-9A-Za-z]{7}$/.test(l?.slug ?? "")), JSON.stringify(extra.map((l) => l?.slug)));
r = await site.post("/v1/links", { destination: "https://example.com/6" });
step("quotas : 6e lien du plan Starter -> 403 quota_exceeded", r.status === 403 && r.json?.code === "quota_exceeded", `statut ${r.status} ${r.text}`);
r = await site.del(`/v1/links/${extra[3].id}`);
step("liens : suppression (204) libère une place", r.status === 204, `statut ${r.status}`);

// ===== Redirection =====
r = await visitor.get(`/${slug}?utm_source=whatsapp`, { referer: "https://www.instagram.com/p/xyz" });
step("redirection : 302 vers la destination, paramètres entrants transmis",
  r.status === 302 && r.headers.get("location") === "https://example.com/produit?id=42&utm_source=whatsapp" && /no-store/.test(r.headers.get("cache-control") ?? ""),
  `statut ${r.status} ${r.headers.get("location")}`);
await visitor.get(`/${slug}`);
r = await visitor.head(`/${slug}`);
step("redirection : HEAD répond 302 (non compté)", r.status === 302, `statut ${r.status}`);
r = await new Client(SITE, "WhatsApp/2.23.20.0 A").get(`/${slug}`);
step("redirection : un aperçu de lien (robot) est redirigé (non compté)", r.status === 302, `statut ${r.status}`);

// ===== QR codes =====
r = await site.post("/v1/qr-codes", { kind: "dynamic", linkId: link.id, foreground: "#243D30", errorCorrection: "Q", title: "Affiche" });
const qr = r.json;
step("QR : création d'un QR dynamique (201) qui encode /q/{code}", r.status === 201 && /\/q\/[0-9A-Za-z]{8}$/.test(qr?.encoded ?? ""), `statut ${r.status} ${r.text}`);
r = await visitor.get(`/q/${qr.code}`);
step("QR : le scan redirige vers la destination du lien", r.status === 302 && r.headers.get("location") === "https://example.com/produit?id=42", `statut ${r.status}`);
for (const [f, type, sig] of [["svg", "image/svg+xml", "<?xml"], ["png", "image/png", "\x89PNG"], ["pdf", "application/pdf", "%PDF"]]) {
  r = await site.get(`/v1/qr-codes/${qr.id}/render?format=${f}&size=512&download=1`);
  step(`QR : export ${f.toUpperCase()} (${type}, en pièce jointe)`, r.status === 200 && (r.headers.get("content-type") ?? "").startsWith(type) && r.raw.subarray(0, 8).toString("latin1").includes(sig) && /attachment/.test(r.headers.get("content-disposition") ?? ""), `statut ${r.status} ${r.headers.get("content-type")}`);
}
r = await site.post("/v1/qr-codes", { kind: "static", contentType: "wifi", fields: { ssid: "Atelier", password: "secret123" } });
step("QR : QR statique Wi-Fi (201, hors quota)", r.status === 201 && r.json?.encoded === "WIFI:T:WPA;S:Atelier;P:secret123;;", `statut ${r.status} ${r.text}`);
r = await site.del(`/v1/links/${link.id}`);
step("QR : un lien suivi par un QR ne peut pas être supprimé (409 link_in_use)", r.status === 409 && r.json?.code === "link_in_use", `statut ${r.status}`);

// ===== Page bio =====
const bioSlug = `bio-${Date.now().toString(36)}`;
r = await site.post("/v1/bio-pages", { slug: bioSlug, title: "Atelier e2e" });
const bio = r.json;
step("bio : création d'une page (201)", r.status === 201 && bio?.publicUrl?.endsWith(`/${bioSlug}`), `statut ${r.status} ${r.text}`);
r = await site.put(`/v1/bio-pages/${bio.id}/blocks`, { blocks: [
  { type: "heading", config: { text: "Nos créations" } },
  { type: "button", config: { label: "Boutique", url: "https://example.com/boutique" } },
  { type: "social", config: { instagram: "https://instagram.com/example" } },
] });
const blockId = r.json?.blocks?.[1]?.id;
step("bio : blocs enregistrés dans l'ordre", r.status === 200 && r.json?.blocks?.map((b) => b.type).join(",") === "heading,button,social", `statut ${r.status} ${r.text}`);
r = await site.put(`/v1/bio-pages/${bio.id}`, { slug: bioSlug, title: "Atelier e2e", description: "Des objets <b>qui</b> ont une histoire", theme: "foret", seoTitle: "Atelier e2e — boutique", noindex: false });
step("bio : réglages (thème, SEO) enregistrés", r.status === 200 && r.json?.theme === "foret", `statut ${r.status} ${r.text}`);
r = await visitor.get(`/${bioSlug}`);
step("bio : page publiée, rendue côté serveur, contenu échappé, CSP sans script",
  r.status === 200 && r.text.includes("Atelier e2e — boutique") && r.text.includes("&lt;b&gt;qui&lt;/b&gt;") && !/<script/i.test(r.text) && /default-src 'none'/.test(r.headers.get("content-security-policy") ?? "") && r.raw.length < 150_000,
  `statut ${r.status}`);
step("bio : badge « Propulsé par Beep »", /Propulsé par/.test(r.text));
r = await visitor.get(`/_b/c/${bio.id}/${blockId}`);
step("bio : clic sur un bouton mesuré puis redirigé", r.status === 302 && r.headers.get("location") === "https://example.com/boutique", `statut ${r.status}`);
r = await site.post("/v1/bio-pages", { slug: `${bioSlug}-2`, title: "Deuxième" });
step("quotas : 2e page bio du plan Starter -> 403 quota_exceeded", r.status === 403 && r.json?.code === "quota_exceeded", `statut ${r.status}`);

// ===== Pages de l'espace client (rendu serveur) =====
for (const p of ["/app", "/app/links", `/app/links/${link.id}`, "/app/qr", "/app/bio", `/app/bio/${bio.id}`, "/app/settings", "/en/app/links"]) {
  r = await site.get(p);
  step(`espace client : ${p} (200)`, r.status === 200 && (p !== "/app" || r.text.includes(email)), `statut ${r.status}`);
}

// ===== Statistiques (agrégats du worker) =====
let stats;
for (let i = 0; i < 40; i++) {
  stats = (await site.get(`/v1/links/${link.id}/stats?days=1`)).json;
  if ((stats?.clicks ?? 0) >= 3) break;
  await sleep(3000);
}
step("stats : 2 clics + 1 scan humains comptés (HEAD et robot exclus)", stats?.clicks === 3, JSON.stringify({ clicks: stats?.clicks, uniques: stats?.uniques }));
step("stats : 1 visiteur unique (empreinte du jour)", stats?.uniques === 1, `uniques ${stats?.uniques}`);
step("stats : répartition par origine (lien / QR)", stats?.breakdowns?.source?.some((b) => b.key === "scan" && b.clicks === 1), JSON.stringify(stats?.breakdowns?.source));
step("stats : Starter = analytics de base, série horaire du jour", stats?.advanced === false && stats?.granularity === "hour" && stats?.series?.length === 24 && !("referrer" in (stats?.breakdowns ?? {})), JSON.stringify(Object.keys(stats?.breakdowns ?? {})));
r = await site.get(`/v1/qr-codes/${qr.id}/stats?days=7`);
step("stats : scans du QR dynamique", r.status === 200 && r.json?.clicks === 1, `statut ${r.status} ${r.text.slice(0, 200)}`);
r = await site.get(`/v1/bio-pages/${bio.id}/stats?days=7`);
step("stats : vues et clics de blocs de la page bio", r.status === 200 && r.json?.clicks >= 1 && r.json?.secondaryClicks === 1, `statut ${r.status} ${r.text.slice(0, 200)}`);
r = await site.get(`/v1/links/${link.id}/stats.csv?days=30`);
step("export CSV : réservé aux plans qui l'incluent (403 feature_unavailable)", r.status === 403 && r.json?.code === "feature_unavailable", `statut ${r.status}`);

// ===== Back office : fiche, override, modération, suspension =====
r = await bo.get(`/api/users?q=${encodeURIComponent(email)}`);
const userId = r.json?.items?.[0]?.id;
step("back office : recherche de l'utilisateur par e-mail", r.status === 200 && r.json?.items?.length === 1 && r.json.items[0].email === email, `statut ${r.status} ${r.text.slice(0, 200)}`);
r = await bo.get(`/api/users/${userId}`);
const workspaceId = r.json?.workspace?.id;
step("back office : fiche (usage face aux quotas)", r.status === 200 && r.json?.usage?.find((u) => u.resource === "links")?.used === 4, `statut ${r.status} ${r.text.slice(0, 300)}`);
r = await bo.put(`/api/workspaces/${workspaceId}/entitlements`, { overrides: { "features.export_csv": 1 }, reason: "bêta : export activé", totp: "000000" });
step("back office : override sans ré-authentification valide -> 403 reauth_required", r.status === 403 && r.json?.code === "reauth_required", `statut ${r.status}`);
r = await bo.put(`/api/workspaces/${workspaceId}/entitlements`, { overrides: { "features.export_csv": 1 }, reason: "bêta : export activé", totp: totp(STAFF_TOTP_SECRET) });
step("back office : override export CSV appliqué", r.status === 200 && r.json?.entitlements?.["features.export_csv"] === 1, `statut ${r.status} ${r.text}`);
r = await site.get(`/v1/links/${link.id}/stats.csv?days=30`);
step("export CSV : disponible après override (text/csv)", r.status === 200 && /text\/csv/.test(r.headers.get("content-type") ?? "") && r.text.includes("section,cle,clics"), `statut ${r.status}`);

r = await bo.get(`/api/links?q=${slug}`);
step("back office : recherche de lien par slug", r.status === 200 && r.json?.items?.[0]?.ownerEmail === email, `statut ${r.status}`);
r = await bo.post(`/api/links/${link.id}/disable`, { reason: "test e2e : contenu signalé" });
step("back office : désactivation d'un lien", r.status === 200 && r.json?.status === "disabled", `statut ${r.status} ${r.text}`);
let blocked;
for (let i = 0; i < 20; i++) {
  blocked = await visitor.get(`/${slug}`);
  if (blocked.status === 410) break;
  await sleep(250);
}
step("redirection : lien désactivé -> page d'avertissement 410, sans redirection (cache invalidé)", blocked.status === 410 && !blocked.headers.get("location"), `statut ${blocked.status}`);
r = await bo.post(`/api/links/${link.id}/enable`);
step("back office : réactivation", r.status === 200 && r.json?.status === "active", `statut ${r.status}`);

r = await bo.post(`/api/users/${userId}/status`, { status: "suspended", reason: "test e2e : suspension", totp: totp(STAFF_TOTP_SECRET) });
step("back office : suspension du compte (TOTP de ré-authentification)", r.status === 200 && r.json?.status === "suspended", `statut ${r.status} ${r.text}`);
r = await site.get("/v1/auth/me");
step("suspension : sessions révoquées", r.status === 401, `statut ${r.status}`);
for (let i = 0; i < 20; i++) {
  blocked = await visitor.get(`/${slug}`);
  if (blocked.status === 410) break;
  await sleep(250);
}
step("suspension : les liens du workspace ne redirigent plus", blocked.status === 410, `statut ${blocked.status}`);
r = await bo.post(`/api/users/${userId}/status`, { status: "active", reason: "test e2e : réactivation", totp: totp(STAFF_TOTP_SECRET) });
step("back office : réactivation du compte", r.status === 200 && r.json?.status === "active", `statut ${r.status}`);

// ===== TOTP, lien magique et second facteur =====
r = await site.post("/v1/auth/login", { email, password });
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

r = await site.post("/v1/auth/magic-link", { email, locale: "fr" });
step("lien magique : demande (202)", r.status === 202, `statut ${r.status}`);
const magic = await lastMailTokenFor(email, "magic");
step("lien magique : e-mail reçu", !!magic);
r = await site.post("/v1/auth/magic-link/consume", { token: magic });
step("lien magique : TOTP actif -> second facteur exigé", r.status === 200 && r.json?.status === "mfa_required" && !!r.json?.token, `statut ${r.status} ${r.text}`);
const mfaToken = r.json?.token;
r = await site.post("/v1/auth/mfa", { token: mfaToken, code: "000000" });
step("second facteur : mauvais code -> 401 invalid_totp", r.status === 401, `statut ${r.status}`);
r = await site.post("/v1/auth/mfa", { token: mfaToken, code: totp(secret) });
step("second facteur : session ouverte", r.status === 200 && [...site.jar.keys()].some((k) => /beep_session$/.test(k)), `statut ${r.status} ${r.text}`);
r = await site.post("/v1/auth/magic-link/consume", { token: magic });
step("lien magique : usage unique", r.status === 400 && r.json?.code === "invalid_token", `statut ${r.status}`);

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

// ===== Back office : pages et journal d'audit =====
r = await bo.get("/api/users?limit=100");
step("back office : l'utilisateur apparaît (via l'API admin du core)", r.status === 200 && r.json?.items?.some((u) => u.email === email && u.emailVerified && u.totpEnabled), `statut ${r.status}`);
for (const p of ["/users", `/users/${userId}`, "/links", `/links/${link.id}`, "/metrics", "/jobs", "/plans", "/audit", "/invitations", "/menu"]) {
  r = await bo.get(p);
  step(`back office : page ${p} rendue côté serveur (200)`, r.status === 200, `statut ${r.status}`);
}
r = await bo.get("/users");
step("back office : sécurité des en-têtes (CSP, noindex)", !!r.headers.get("content-security-policy") && /noindex/i.test(r.headers.get("x-robots-tag") ?? ""), "en-têtes manquants");
r = await bo.get("/api/metrics");
step("back office : métriques (inscriptions, activation, retard du worker)", r.status === 200 && r.json?.core?.users?.total >= 1 && r.json?.core?.worker?.lagSeconds !== undefined && r.json?.backofficeBytes > 0, `statut ${r.status} ${r.text.slice(0, 200)}`);
r = await bo.get("/api/jobs");
step("back office : jobs du worker (agrégats réussis)", r.status === 200 && r.json?.items?.find((j) => j.name === "analytics.rollup")?.lastOk === true, `statut ${r.status} ${r.text.slice(0, 300)}`);
r = await bo.get("/api/audit?action=links.");
step("back office : journal d'audit consultable, chaîne intègre", r.status === 200 && r.json?.intact === true && r.json?.items?.some((e) => e.action === "links.disable"), `statut ${r.status} ${r.text.slice(0, 200)}`);

const sql = (q) => execFileSync("docker", ["compose", "-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "exec", "-T", "postgres", "sh", "-c", `psql -U "$POSTGRES_USER" -d backoffice -tAc "${q}"`], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
try {
  const actions = sql(`select string_agg(distinct action, ',') from audit_log where actor_email = '${STAFF_EMAIL}'`).split(",");
  for (const a of ["staff.login", "staff.login_failed", "invitations.create", "users.list", "users.view", "entitlements.override", "links.search", "links.disable", "links.enable", "users.suspend", "users.reactivate", "audit.read"]) {
    step(`audit : action « ${a} » journalisée`, actions.includes(a), `actions : ${actions.join(",")}`);
  }
  let blockedUpdate = false;
  try { sql("update audit_log set action = 'forged'"); } catch (e) { blockedUpdate = /ajout seul/.test(String(e.stderr)); }
  step("audit : UPDATE refusé par la base (append-only)", blockedUpdate);
} catch (e) {
  step("audit : accès à la base", false, String(e.message).slice(0, 200));
}

await bo.post("/api/auth/logout");
r = await bo.get("/api/auth/me");
step("back office : déconnexion -> 401", r.status === 401, `statut ${r.status}`);

console.log(failures ? `\n${failures} échec(s)` : "\nTous les contrôles passent.");
process.exit(failures ? 1 : 0);
