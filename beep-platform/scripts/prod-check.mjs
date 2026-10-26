// Inspect resolved Compose JSON on stdin; never print configuration or secret values.
import { pathToFileURL } from "node:url";

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
      errors.push(`${key} : origine HTTPS publique requise, sans chemin ni paramètres`);
    }
  };
  const site = publicOrigin(core.BEEP_PUBLIC_URL, "BEEP_PUBLIC_URL");
  const dashboard = publicOrigin(bo.BO_PUBLIC_URL, "BO_PUBLIC_URL");
  if (site && (proxy.BEEP_SITE !== site.hostname || web.BEEP_PUBLIC_URL !== core.BEEP_PUBLIC_URL ||
      services.web?.build?.args?.BEEP_PUBLIC_URL !== core.BEEP_PUBLIC_URL)) errors.push("BEEP_DOMAIN/BEEP_PUBLIC_URL : domaine, build et runtime doivent correspondre");
  if (dashboard && (proxy.BO_SITE !== dashboard.hostname || dashboard.hostname !== `dashboard.${site?.hostname}`)) errors.push("BO_PUBLIC_URL : attendu sur dashboard.<BEEP_DOMAIN>");
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
    const errors = validateProduction(JSON.parse(input));
    if (errors.length) {
      console.error("Configuration de production incomplète :\n" + errors.map((error) => `- ${error}`).join("\n"));
      process.exitCode = 1;
    } else console.log("Configuration de production valide (aucun secret affiché).");
  } catch {
    console.error("Impossible de valider la configuration Compose JSON.");
    process.exitCode = 1;
  }
}
