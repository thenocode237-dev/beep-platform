import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, rmSync, copyFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { validateProduction, validateProductionFiles } from "./prod-check.mjs";

function production() {
  const key = Buffer.alloc(32, 7).toString("base64");
  return { services: {
    core: { image: "beep/core:v1.0.0", environment: {
      BEEP_PUBLIC_URL: "https://beepclick.app", ADMIN_API_TOKEN: "service-secret",
      TOTP_ENCRYPTION_KEY: key, BEEP_CONVERSION_SECRET: key,
      PAYMENT_PROVIDER: "notchpay", NOTCHPAY_PUBLIC_KEY: "provider-key", NOTCHPAY_HASH_KEY: "provider-secret",
      BILLING_SELLER_ADDRESS: "Address", BILLING_SELLER_TAX_ID: "Registration",
      URL_SAFETY_PROVIDER: "web_risk", WEB_RISK_API_KEY: "safety-key",
      SMTP_HOST: "smtp.example.com", MAIL_FROM: "Beepclick <hello@beepclick.app>",
    } },
    web: { build: { args: { BEEP_PUBLIC_URL: "https://beepclick.app" } }, environment: {
      BEEP_PUBLIC_URL: "https://beepclick.app", LEGAL_PUBLISHER: "Beepclick", LEGAL_ADDRESS: "Address",
      LEGAL_REGISTRATION: "Registration", LEGAL_CONTACT: "hello@beepclick.app", LEGAL_HOST: "Host",
    } },
    "backoffice-api": { networks: { admin: {}, backend: {}, db: {} }, environment: { BO_PUBLIC_URL: "https://beep-admin.tail123.ts.net", BO_ENCRYPTION_KEY: key } },
    "backoffice-web": { networks: { admin: {} } },
    "backoffice-vpn": { networks: { admin: {}, "admin-egress": {} },
      volumes: [{ type: "volume", source: "tailscale_state", target: "/var/lib/tailscale" }],
      secrets: [{ source: "tailscale_authkey" }], environment: {
        BO_PUBLIC_URL: "https://beep-admin.tail123.ts.net", TS_HOSTNAME: "beep-admin", TS_USERSPACE: "true",
        TS_AUTH_ONCE: "true", TS_STATE_DIR: "/var/lib/tailscale", TS_ROUTES: "",
        TS_ENABLE_HEALTH_CHECK: "true", TS_LOCAL_ADDR_PORT: "127.0.0.1:9002", TS_SERVE_CONFIG: "/etc/tailscale/serve/serve.json",
        TS_EXTRA_ARGS: "--advertise-tags=tag:beep-admin --accept-routes=false --ssh=false",
      } },
    "backoffice-gateway": { network_mode: "service:backoffice-vpn" },
    proxy: { networks: { edge: {} }, depends_on: { core: {}, web: {} }, environment: { BEEP_SITE: "beepclick.app", ACME_EMAIL: "ops@beepclick.app" } },
    postgres: { environment: { POSTGRES_USER: "postgres", POSTGRES_PASSWORD: "pg-secret", BEEP_DB_PASSWORD: "db-secret", BO_DB_PASSWORD: "bo-secret", WALG_S3_PREFIX: "s3://backups/pg" } },
    "pg-backup": { environment: { WALG_S3_PREFIX: "s3://backups/pg", AWS_ACCESS_KEY_ID: "backup-id", AWS_SECRET_ACCESS_KEY: "backup-secret" } },
  }, networks: { admin: { internal: true } }, secrets: { tailscale_authkey: { file: "/secrets/authkey" } } };
}

test("accepts a coherent production configuration", () => {
  assert.deepEqual(validateProduction(production()), []);
});

test("rejects local defaults and sandbox before any deployment", () => {
  const config = production();
  Object.assign(config.services.core.environment, { BEEP_PUBLIC_URL: "http://beep.localhost", PAYMENT_PROVIDER: "sandbox", SMTP_HOST: "mailpit" });
  config.services.core.image = "beep/core:dev";
  const errors = validateProduction(config).join("\n");
  for (const key of ["BEEP_PUBLIC_URL", "PAYMENT_PROVIDER", "SMTP_HOST", "BEEP_VERSION"]) assert.match(errors, new RegExp(key));
});

test("rejects build/runtime origin drift and incomplete backups", () => {
  const config = production();
  config.services.web.build.args.BEEP_PUBLIC_URL = "http://beep.localhost";
  config.services["pg-backup"].environment.AWS_SECRET_ACCESS_KEY = "";
  const errors = validateProduction(config).join("\n");
  assert.match(errors, /build et runtime/);
  assert.match(errors, /AWS_SECRET_ACCESS_KEY/);
});

test("validates encryption keys without exposing secret values", () => {
  const config = production();
  config.services.core.environment.TOTP_ENCRYPTION_KEY = "DO-NOT-LOG-THIS-SECRET";
  const errors = validateProduction(config).join("\n");
  assert.match(errors, /TOTP_ENCRYPTION_KEY/);
  assert.doesNotMatch(errors, /DO-NOT-LOG-THIS-SECRET|backup-secret|pg-secret/);
});

test("rejects partial configurations instead of treating them as production", () => {
  assert.ok(validateProduction({ services: {} }).length > 10);
});

test("rejects backup services isolated from their remote storage", () => {
  const config = production();
  config.networks.db = { internal: true };
  config.services.postgres.networks = { db: {} };
  config.services["pg-backup"].networks = { db: {} };
  assert.equal(validateProduction(config).filter((error) => error.includes("réseau sortant")).length, 2);
  config.networks.egress = {};
  config.services.postgres.networks.egress = {};
  config.services["pg-backup"].networks.egress = {};
  assert.deepEqual(validateProduction(config), []);
});

test("rejects a public backoffice URL and VPN origin drift", () => {
  const config = production();
  config.services["backoffice-api"].environment.BO_PUBLIC_URL = "https://dashboard.beepclick.app";
  const errors = validateProduction(config).join("\n");
  assert.match(errors, /beep-admin/);
  assert.match(errors, /même origine privée/);
});

for (const name of ["backoffice-api", "backoffice-web", "backoffice-vpn", "backoffice-gateway"]) {
  test(`rejects host port publication for ${name}`, () => {
    const config = production();
    config.services[name].ports = [{ published: "8080", target: 8080 }];
    assert.match(validateProduction(config).join("\n"), /ports hôte/);
  });
  test(`rejects a public network attachment for ${name}`, () => {
    const config = production();
    config.services[name].networks = { edge: {}, admin: {} };
    assert.match(validateProduction(config).join("\n"), /réseau public/);
  });
}

test("rejects raw Tailscale keys without logging their contents", () => {
  const config = production();
  config.services["backoffice-vpn"].environment.TS_AUTHKEY = "DO-NOT-LOG-THIS-SECRET";
  const errors = validateProduction(config).join("\n");
  assert.match(errors, /secret en environnement/);
  assert.doesNotMatch(errors, /DO-NOT-LOG/);
});

test("rejects missing persistent state, secrets, and public proxy dependencies", () => {
  const config = production();
  config.services["backoffice-vpn"].volumes = [];
  config.services["backoffice-vpn"].secrets = [];
  config.services.proxy.depends_on["backoffice-vpn"] = {};
  const errors = validateProduction(config).join("\n");
  for (const pattern of [/volume persistant/, /secret Docker/, /dépendance/]) assert.match(errors, pattern);
});

test("checks the actual mounted Serve file and missing enrollment keys without logging contents", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "beep-prod-check-"));
  try {
    const config = production();
    config.secrets.tailscale_authkey.file = path.join(directory, "authkey");
    writeFileSync(config.secrets.tailscale_authkey.file, "tskey-auth-DO-NOT-LOG");
    copyFileSync(new URL("../infra/tailscale/serve/serve.json", import.meta.url), path.join(directory, "serve.json"));
    config.services["backoffice-vpn"].volumes.push({ type: "bind", source: directory, target: "/etc/tailscale/serve", read_only: true });
    assert.deepEqual(validateProductionFiles(config), []);
    const checker = fileURLToPath(new URL("./prod-check.mjs", import.meta.url));
    const valid = spawnSync(process.execPath, [checker], { input: JSON.stringify(config), encoding: "utf8" });
    assert.equal(valid.status, 0, valid.stderr);
    writeFileSync(path.join(directory, "serve.json"), '{"AllowFunnel":{"example:443":true}}');
    writeFileSync(config.secrets.tailscale_authkey.file, "INVALID-DO-NOT-LOG");
    const errors = validateProductionFiles(config).join("\n");
    assert.match(errors, /TAILSCALE_AUTHKEY_FILE/);
    assert.match(errors, /sans Funnel/);
    assert.doesNotMatch(errors, /DO-NOT-LOG/);
    const invalid = spawnSync(process.execPath, [checker], { input: JSON.stringify(config), encoding: "utf8" });
    assert.equal(invalid.status, 1);
    assert.match(invalid.stderr, /sans Funnel/);
    assert.doesNotMatch(invalid.stdout + invalid.stderr, /DO-NOT-LOG|provider-secret|pg-secret/);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});
