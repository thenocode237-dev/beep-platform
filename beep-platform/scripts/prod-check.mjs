// Inspect resolved Compose JSON on stdin; never print configuration or secret values.
import { pathToFileURL, fileURLToPath } from "node:url";
import { readFileSync, statSync } from "node:fs";
import path from "node:path";

const networkNames = (service) => Array.isArray(service?.networks) ? service.networks : Object.keys(service?.networks ?? {});

export function validateBackofficeIsolation(config) {
  const errors = [];
  const services = config.services ?? {};
  const vpn = services["backoffice-vpn"] ?? {};
  const gateway = services["backoffice-gateway"] ?? {};
  const env = vpn.environment ?? {};
  const publicNetworks = networkNames(services.proxy);
  for (const name of ["backoffice-api", "backoffice-web", "backoffice-vpn", "backoffice-gateway"]) {
    const service = services[name];
    if (!service) { errors.push(`${name} : service privé requis`); continue; }
    if (service.ports?.length || service.network_mode === "host" || service.privileged || service.cap_add?.length || service.devices?.length) errors.push(`${name} : ports hôte et privilèges réseau interdits`);
    if (networkNames(service).some((network) => publicNetworks.includes(network) || network === "edge")) errors.push(`${name} : raccordement au réseau public interdit`);
  }
  if (config.networks?.admin?.internal !== true) errors.push("admin : réseau Docker interne requis");
  for (const name of ["backoffice-api", "backoffice-web", "backoffice-vpn"]) {
    if (!networkNames(services[name]).includes("admin")) errors.push(`${name} : réseau admin requis`);
  }
  if (networkNames(services["backoffice-api"]).some((network) => !["admin", "backend", "db"].includes(network))) errors.push("backoffice-api : seuls admin, backend et db sont autorisés");
  if (networkNames(vpn).some((network) => !["admin", "admin-egress"].includes(network)) || !networkNames(vpn).includes("admin-egress") || config.networks?.["admin-egress"]?.internal === true) errors.push("Tailscale : sortie dédiée admin-egress requise");
  for (const [name, service] of Object.entries(services)) {
    if (networkNames(service).includes("admin") && service.ports?.length) errors.push(`${name} : aucun port hôte autorisé sur le réseau admin`);
  }
  if (networkNames(services["backoffice-web"]).some((network) => network !== "admin")) errors.push("backoffice-web : seul le réseau admin est autorisé");
  if (gateway.network_mode !== "service:backoffice-vpn" || networkNames(gateway).length) errors.push("backoffice-gateway : espace réseau du VPN requis");
  const dependencies = services.proxy?.depends_on ?? {};
  if ((Array.isArray(dependencies) ? dependencies : Object.keys(dependencies)).some((name) => name.startsWith("backoffice-"))) errors.push("proxy : aucune dépendance au backoffice ou au VPN autorisée");
  if (services.proxy?.environment?.BO_SITE) errors.push("BO_SITE : route publique du backoffice interdite");
  if (env.TS_USERSPACE !== "true" || env.TS_AUTH_ONCE !== "true" || env.TS_STATE_DIR !== "/var/lib/tailscale" || env.TS_HOSTNAME !== "beep-admin") errors.push("Tailscale : mode userspace, identité persistante et nom beep-admin requis");
  if (env.TS_AUTHKEY || env.TS_AUTH_KEY || env.TS_CLIENT_SECRET || env.TS_CLIENT_ID || env.TS_DEST_IP || env.TS_ROUTES || env.TS_TAILNET_TARGET_IP || env.TS_TAILNET_TARGET_FQDN || env.TS_TAILSCALED_EXTRA_ARGS || env.TS_EXTRA_ARGS !== "--advertise-tags=tag:beep-admin --accept-routes=false --ssh=false") errors.push("Tailscale : secret en environnement, routage ou options non autorisées");
  if (env.TS_ENABLE_HEALTH_CHECK !== "true" || env.TS_LOCAL_ADDR_PORT !== "127.0.0.1:9002" || env.TS_SERVE_CONFIG !== "/etc/tailscale/serve/serve.json") errors.push("Tailscale : Serve privé et contrôle de santé local requis");
  if (!vpn.volumes?.some((mount) => mount.type === "volume" && mount.target === "/var/lib/tailscale")) errors.push("Tailscale : volume persistant requis");
  if (!vpn.secrets?.some((secret) => (typeof secret === "string" ? secret : secret.source) === "tailscale_authkey") || !config.secrets?.tailscale_authkey?.file) errors.push("Tailscale : secret Docker sur fichier requis");
  return errors;
}

export function validateServeConfig(serve, domain = "${TS_CERT_DOMAIN}") {
  const port = `${domain}:443`;
  return Object.keys(serve.TCP ?? {}).length === 1 && serve.TCP?.["443"]?.HTTPS === true &&
    !serve.TCP["443"].TCPForward && Object.keys(serve.Web ?? {}).length === 1 &&
    Object.keys(serve.Web?.[port]?.Handlers ?? {}).length === 1 &&
    serve.Web[port].Handlers["/"]?.Proxy === "http://127.0.0.1:8080" &&
    Object.values(serve.AllowFunnel ?? {}).every((allowed) => allowed === false) && !serve.Services;
}

// Filesystem checks belong to the CLI so unit tests can use synthetic configurations.
export function validateProductionFiles(config) {
  const errors = [];
  try {
    const secret = config.secrets?.tailscale_authkey?.file;
    if (!secret || !statSync(secret).isFile() || !readFileSync(secret, "utf8").trim().startsWith("tskey-auth-")) throw new Error();
    const relative = path.relative(fileURLToPath(new URL("../", import.meta.url)), path.resolve(secret));
    if (!relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative) && !relative.startsWith(`secrets${path.sep}`)) errors.push("TAILSCALE_AUTHKEY_FILE : placer le secret dans secrets/ ou hors du contexte de build");
  } catch { errors.push("TAILSCALE_AUTHKEY_FILE : fichier de clé d'inscription Tailscale requis"); }
  try {
    const mount = config.services?.["backoffice-vpn"]?.volumes?.find((volume) => volume.target === "/etc/tailscale/serve");
    if (mount?.type !== "bind" || mount.read_only !== true) throw new Error();
    const serve = JSON.parse(readFileSync(path.join(mount.source, "serve.json"), "utf8"));
    if (!validateServeConfig(serve)) throw new Error();
  } catch { errors.push("Tailscale Serve : seul HTTPS 443 vers la passerelle locale, sans Funnel, est autorisé"); }
  return errors;
}

export function validateProduction(config) {
  const errors = [];
  const services = config.services ?? {};
  const core = services.core?.environment ?? {};
  const web = services.web?.environment ?? {};
  const bo = services["backoffice-api"]?.environment ?? {};
  const pg = services.postgres?.environment ?? {};
  const backup = services["pg-backup"]?.environment ?? {};
  const proxy = services.proxy?.environment ?? {};
  const requireValue = (env, key) => {
    const value = String(env[key] ?? "").trim();
    if (!value || /^(change-me|sandbox-secret)$/i.test(value)) errors.push(`${key} : valeur de production requise`);
    return value;
  };
  const publicOrigin = (raw, key) => {
    try {
      const url = new URL(raw);
      if (url.protocol !== "https:" || url.username || url.password || url.port || url.pathname !== "/" || url.search || url.hash ||
          !url.hostname.includes(".") || /(^|\.)(localhost|local|test|example|invalid)$/.test(url.hostname)) throw new Error();
      return url;
    } catch {
      errors.push(`${key} : origine HTTPS requise, sans chemin ni paramètres`);
    }
  };
  const site = publicOrigin(core.BEEP_PUBLIC_URL, "BEEP_PUBLIC_URL");
  const dashboard = publicOrigin(bo.BO_PUBLIC_URL, "BO_PUBLIC_URL");
  if (site && (proxy.BEEP_SITE !== site.hostname || web.BEEP_PUBLIC_URL !== core.BEEP_PUBLIC_URL ||
      services.web?.build?.args?.BEEP_PUBLIC_URL !== core.BEEP_PUBLIC_URL)) errors.push("BEEP_DOMAIN/BEEP_PUBLIC_URL : domaine, build et runtime doivent correspondre");
  if (dashboard && (!/^beep-admin\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.ts\.net$/.test(dashboard.hostname) || dashboard.origin !== bo.BO_PUBLIC_URL)) errors.push("BO_PUBLIC_URL : attendu sur https://beep-admin.<réseau>.ts.net, sans slash final");
  if (services["backoffice-vpn"]?.environment?.BO_PUBLIC_URL !== bo.BO_PUBLIC_URL) errors.push("BO_PUBLIC_URL : le VPN et le backoffice doivent partager la même origine privée");
  errors.push(...validateBackofficeIsolation(config));
  if (!proxy.ACME_EMAIL || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(proxy.ACME_EMAIL) || /@.*\.(example|invalid)$/.test(proxy.ACME_EMAIL)) errors.push("ACME_EMAIL : adresse de production requise");
  if (!services.core?.image || /:(dev|latest)$/.test(services.core.image)) errors.push("BEEP_VERSION : version de release explicite requise (ni dev ni latest)");
  for (const key of ["POSTGRES_USER", "POSTGRES_PASSWORD", "BEEP_DB_PASSWORD", "BO_DB_PASSWORD"]) requireValue(pg, key);
  requireValue(core, "ADMIN_API_TOKEN");
  for (const [env, key] of [[core, "TOTP_ENCRYPTION_KEY"], [bo, "BO_ENCRYPTION_KEY"], [core, "BEEP_CONVERSION_SECRET"]]) {
    const value = requireValue(env, key);
    if (value && (!/^[A-Za-z0-9+/]{43}=$/.test(value) || Buffer.from(value, "base64").length !== 32)) errors.push(`${key} : 32 octets encodés en base64 requis`);
  }
  if (core.PAYMENT_PROVIDER !== "notchpay") errors.push("PAYMENT_PROVIDER : notchpay requis en production (sandbox refusée par le serveur)");
  for (const key of ["NOTCHPAY_PUBLIC_KEY", "NOTCHPAY_HASH_KEY", "BILLING_SELLER_ADDRESS", "BILLING_SELLER_TAX_ID"]) requireValue(core, key);
  if (core.URL_SAFETY_PROVIDER !== "web_risk") errors.push("URL_SAFETY_PROVIDER : web_risk requis pour ce déploiement commercial");
  requireValue(core, "WEB_RISK_API_KEY");
  if (!core.SMTP_HOST || /^(mailpit|localhost|127\.0\.0\.1)$/.test(core.SMTP_HOST)) errors.push("SMTP_HOST : serveur SMTP de production requis");
  if (!core.MAIL_FROM || /@(beep\.localhost|.*\.example)[>\s]*$/.test(core.MAIL_FROM)) errors.push("MAIL_FROM : expéditeur de production requis");
  for (const key of ["LEGAL_PUBLISHER", "LEGAL_ADDRESS", "LEGAL_REGISTRATION", "LEGAL_CONTACT", "LEGAL_HOST"]) requireValue(web, key);
  for (const key of ["WALG_S3_PREFIX", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"]) requireValue(backup, key);
  if (!/^s3:\/\/[^/]+\/.+/.test(backup.WALG_S3_PREFIX ?? "")) errors.push("BACKUP_S3_PREFIX : préfixe s3://bucket/chemin requis");
  if (pg.WALG_S3_PREFIX !== backup.WALG_S3_PREFIX) errors.push("BACKUP_S3_PREFIX : archivage WAL et sauvegardes doivent partager le même dépôt");
  for (const name of ["postgres", "pg-backup"]) {
    const attached = services[name]?.networks ?? { default: {} };
    const names = Array.isArray(attached) ? attached : Object.keys(attached);
    if (names.length && names.every((network) => config.networks?.[network]?.internal === true)) errors.push(`${name} : réseau sortant requis pour joindre le stockage S3`);
  }
  return errors;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let input = "";
  for await (const chunk of process.stdin) input += chunk;
  try {
    const config = JSON.parse(input);
    const errors = [...validateProduction(config), ...validateProductionFiles(config)];
    if (errors.length) {
      console.error("Configuration de production incomplète :\n" + errors.map((error) => `- ${error}`).join("\n"));
      process.exitCode = 1;
    } else console.log("Configuration de production valide (aucun secret affiché).");
  } catch {
    console.error("Impossible de valider la configuration Compose JSON.");
    process.exitCode = 1;
  }
}
