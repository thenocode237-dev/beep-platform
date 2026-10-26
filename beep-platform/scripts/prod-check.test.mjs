import test from "node:test";
import assert from "node:assert/strict";
import { validateProduction } from "./prod-check.mjs";

function production() {
  const key = Buffer.alloc(32, 7).toString("base64");
  return { services: {
    core: { image: "beep/core:v1.0.0", environment: {
      BEEP_PUBLIC_URL: "https://beep.example.com", ADMIN_API_TOKEN: "service-secret",
      TOTP_ENCRYPTION_KEY: key, BEEP_CONVERSION_SECRET: key,
      PAYMENT_PROVIDER: "notchpay", NOTCHPAY_PUBLIC_KEY: "provider-key", NOTCHPAY_HASH_KEY: "provider-secret",
      BILLING_SELLER_ADDRESS: "Address", BILLING_SELLER_TAX_ID: "Registration",
      URL_SAFETY_PROVIDER: "web_risk", WEB_RISK_API_KEY: "safety-key",
      SMTP_HOST: "smtp.example.com", MAIL_FROM: "Beep <hello@beep.example.com>",
    } },
    web: { build: { args: { BEEP_PUBLIC_URL: "https://beep.example.com" } }, environment: {
      BEEP_PUBLIC_URL: "https://beep.example.com", LEGAL_PUBLISHER: "Beep", LEGAL_ADDRESS: "Address",
      LEGAL_REGISTRATION: "Registration", LEGAL_CONTACT: "hello@beep.example.com", LEGAL_HOST: "Host",
    } },
    "backoffice-api": { environment: { BO_PUBLIC_URL: "https://dashboard.beep.example.com", BO_ENCRYPTION_KEY: key } },
    proxy: { environment: { BEEP_SITE: "beep.example.com", BO_SITE: "dashboard.beep.example.com", ACME_EMAIL: "ops@beep.example.com" } },
    postgres: { environment: { POSTGRES_USER: "postgres", POSTGRES_PASSWORD: "pg-secret", BEEP_DB_PASSWORD: "db-secret", BO_DB_PASSWORD: "bo-secret", WALG_S3_PREFIX: "s3://backups/pg" } },
    "pg-backup": { environment: { WALG_S3_PREFIX: "s3://backups/pg", AWS_ACCESS_KEY_ID: "backup-id", AWS_SECRET_ACCESS_KEY: "backup-secret" } },
  } };
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
  config.networks = { db: { internal: true } };
  config.services.postgres.networks = { db: {} };
  config.services["pg-backup"].networks = { db: {} };
  assert.equal(validateProduction(config).filter((error) => error.includes("réseau sortant")).length, 2);
  config.networks.egress = {};
  config.services.postgres.networks.egress = {};
  config.services["pg-backup"].networks.egress = {};
  assert.deepEqual(validateProduction(config), []);
});
