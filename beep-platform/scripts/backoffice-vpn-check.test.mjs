import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateServeConfig } from "./prod-check.mjs";
import { validateBackofficeVpn } from "./backoffice-vpn-check.mjs";

const template = readFileSync(new URL("../infra/tailscale/serve/serve.json", import.meta.url), "utf8");
const domain = "beep-admin.tail123.ts.net";
const state = () => ({ origin: `https://${domain}`, status: { BackendState: "Running", Self: { DNSName: `${domain}.`, Online: true } }, serve: JSON.parse(template.replaceAll("${TS_CERT_DOMAIN}", domain)) });

test("accepts the actual private Serve template and a matching live node", () => {
  assert.equal(validateServeConfig(JSON.parse(template)), true);
  assert.deepEqual(validateBackofficeVpn(state()), []);
});

test("rejects Funnel, extra listeners, handlers and arbitrary proxy destinations", () => {
  for (const mutate of [
    (serve) => { serve.AllowFunnel[`${domain}:443`] = true; },
    (serve) => { serve.TCP["80"] = { TCPForward: "127.0.0.1:8080" }; },
    (serve) => { serve.TCP["443"].TCPForward = "127.0.0.1:8080"; },
    (serve) => { serve.Web[`${domain}:443`].Handlers["/debug"] = { Proxy: "http://127.0.0.1:2019" }; },
    (serve) => { serve.Web[`${domain}:443`].Handlers["/"].Proxy = "http://backoffice-api:8081"; },
  ]) {
    const input = state();
    mutate(input.serve);
    assert.ok(validateBackofficeVpn(input).some((error) => error.includes("Funnel")));
  }
});

test("rejects an offline, renamed or mismatched node", () => {
  for (const mutate of [
    (input) => { input.status.BackendState = "NeedsLogin"; },
    (input) => { input.status.Self.Online = false; },
    (input) => { input.status.Self.DNSName = "beep-admin-1.tail123.ts.net."; },
    (input) => { input.origin = "https://dashboard.beep.click"; },
  ]) {
    const input = state();
    mutate(input);
    assert.ok(validateBackofficeVpn(input).length > 0);
  }
});

test("handles malformed status without accepting an unconfigured node", () => {
  assert.ok(validateBackofficeVpn({}).length > 0);
});
