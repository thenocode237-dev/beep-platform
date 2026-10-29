// Real Docker/Caddy routing and namespace tests; no tailnet credentials or production services.
import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { validateBackofficeIsolation } from "./prod-check.mjs";

const root = fileURLToPath(new URL("../", import.meta.url));
const docker = (...args) => execFileSync("docker", args, { cwd: root, encoding: "utf8", timeout: 90_000, stdio: ["ignore", "pipe", "pipe"] });
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

test("production Compose isolates staff services while development stays accessible", () => {
  function compose(overlay) {
    return JSON.parse(execFileSync("docker", ["compose", "--env-file", ".env.production.example", "-f", "docker-compose.yml", "-f", overlay, "--profile", "prod", "config", "--format", "json"], {
      cwd: root, encoding: "utf8", env: { ...process.env, BEEP_DOMAIN: "beepclick.app", BO_PUBLIC_URL: "https://beep-admin.tail123.ts.net" }, stdio: ["ignore", "pipe", "pipe"],
    }));
  }
  const prod = compose("docker-compose.prod.yml");
  assert.deepEqual(validateBackofficeIsolation(prod), []);
  assert.deepEqual(Object.keys(prod.services["backoffice-api"].networks).sort(), ["admin", "backend", "db"]);
  const dev = compose("docker-compose.dev.yml");
  assert.ok(Object.hasOwn(dev.services["backoffice-api"].networks, "edge"));
  assert.ok(Object.hasOwn(dev.services["backoffice-web"].networks, "edge"));
  assert.ok(dev.services.proxy.depends_on["backoffice-web"]);
  assert.equal(dev.services["backoffice-vpn"], undefined);
});

test("private gateway binds only loopback; public Caddy rejects retired dashboard over HTTP and HTTPS", { timeout: 180_000 }, async (t) => {
  const id = `beep-isolation-${process.pid}-${Date.now()}`;
  const temp = mkdtempSync(path.join(tmpdir(), "beep-isolation-"));
  const containers = [];
  const networks = [];
  function run(name, args) {
    const fullName = `${id}-${name}`;
    docker("run", "-d", "--name", fullName, ...args);
    containers.push(fullName);
    return fullName;
  }
  const request = (container, url, headers = []) => docker("exec", container, "wget", "--no-check-certificate", "-S", "-O", "-", "-T", "3", ...headers.flatMap((header) => ["--header", header]), url);
  async function ready(container, url) {
    for (let attempt = 0; attempt < 40; attempt++) {
      try { return request(container, url); } catch { await delay(250); }
    }
    assert.fail(`Gateway did not start: ${docker("logs", container)}`);
  }
  function denied(container, url, headers = []) {
    const result = spawnSync("docker", ["exec", container, "wget", "--no-check-certificate", "-S", "-O", "-", "-T", "3", ...headers.flatMap((header) => ["--header", header]), url], { encoding: "utf8", timeout: 10_000 });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /404 Not Found/);
    assert.doesNotMatch(result.stdout, /staff-ui|staff-api/);
  }
  try {
    for (const suffix of ["admin", "edge"]) {
      const name = `${id}-${suffix}`;
      docker("network", "create", ...(suffix === "admin" ? ["--internal"] : []), name);
      networks.push(name);
    }
    const [admin, edge] = networks;
    writeFileSync(path.join(temp, "mock.caddy"), `{
  admin off
  auto_https off
}
http://:8081 {
  respond "staff-api proto={http.request.header.X-Forwarded-Proto} ip={http.request.header.X-Forwarded-For} identity={http.request.header.Tailscale-User-Login}"
}
http://:3000 {
  respond "staff-ui"
}
http://:8080 {
  respond "public-redirector"
}
`);
    const mock = run("mock", ["--network", admin, "--network-alias", "backoffice-api", "--network-alias", "backoffice-web", "-v", `${temp}:/test:ro`, "caddy:2", "caddy", "run", "--config", "/test/mock.caddy", "--adapter", "caddyfile"]);
    // A separate public stub must not have any route to the real staff mock.
    writeFileSync(path.join(temp, "public-mock.caddy"), "{\n admin off\n auto_https off\n}\nhttp://:8080 {\n respond \"public-api\"\n}\nhttp://:3000 {\n respond \"public-web\"\n}\n");
    run("public-mock", ["--network", edge, "--network-alias", "core", "--network-alias", "redirector", "--network-alias", "web", "-v", `${temp}:/test:ro`, "caddy:2", "caddy", "run", "--config", "/test/public-mock.caddy", "--adapter", "caddyfile"]);
    const vpn = run("namespace", ["--network", admin, "--network-alias", "vpn", "--entrypoint", "/bin/sh", "caddy:2", "-c", "sleep 300"]);
    const gateway = run("gateway", ["--network", `container:${vpn}`, "-v", `${root}:/repo:ro`, "caddy:2", "caddy", "run", "--config", "/repo/infra/caddy/Caddyfile.backoffice"]);
    await ready(gateway, "http://127.0.0.1:8080/healthz");
    await t.test("UI and API share one private origin with HTTPS forwarding", () => {
      assert.equal(request(gateway, "http://127.0.0.1:8080/login"), "staff-ui");
      const result = request(gateway, "http://127.0.0.1:8080/api/auth/session", ["X-Forwarded-Proto: http", "X-Forwarded-For: 100.100.10.20", "Tailscale-User-Login: attacker@invalid"]);
      assert.match(result, /staff-api proto=https ip=100\.100\.10\.20/);
      assert.doesNotMatch(result, /attacker/);
      for (const route of ["/internal/admin", "/actuator/health"]) denied(gateway, `http://127.0.0.1:8080${route}`);
    });
    await t.test("another container cannot directly reach the private listener", () => {
      const result = spawnSync("docker", ["exec", mock, "wget", "-qO-", "-T", "2", "http://vpn:8080/login"], { encoding: "utf8", timeout: 5_000 });
      assert.notEqual(result.status, 0);
      assert.doesNotMatch(result.stdout, /staff-ui/);
    });
    // Use the production routes verbatim. Replace only ACME issuance with an
    // internal CA so HTTPS/Host bypasses can be tested offline on a Docker network.
    const publicConfig = JSON.parse(docker("run", "--rm", "-e", "BEEP_SITE=beepclick.app", "-e", "ACME_EMAIL=ops@beepclick.app", "-v", `${root}:/repo:ro`, "caddy:2", "caddy", "adapt", "--config", "/repo/infra/caddy/Caddyfile"));
    publicConfig.apps.tls.automation.policies = [{ issuers: [{ module: "internal" }], on_demand: true }];
    writeFileSync(path.join(temp, "public.json"), JSON.stringify(publicConfig));
    const proxy = run("public", ["--network", edge, "--network-alias", "beepclick.app", "--network-alias", "dashboard.beepclick.app", "--network-alias", "customer.invalid", "-v", `${temp}:/test:ro`, "caddy:2", "caddy", "run", "--config", "/test/public.json"]);
    await ready(proxy, "https://beepclick.app/");
    await t.test("old public host is denied including HTTPS, forged Host and API routes", () => {
      for (const route of ["/login", "/api/auth/session", "/_next/static/app.js"]) {
        denied(proxy, `http://dashboard.beepclick.app${route}`);
        denied(proxy, `https://dashboard.beepclick.app${route}`);
        denied(proxy, `https://beepclick.app${route}`, ["Host: dashboard.beepclick.app"]);
      }
    });
    await t.test("public pages, client area, API and custom domains still route", () => {
      for (const route of ["/", "/app", "/en/features"]) assert.equal(request(proxy, `https://beepclick.app${route}`), "public-web");
      assert.equal(request(proxy, "https://beepclick.app/v1/health"), "public-api");
      assert.equal(request(proxy, "https://customer.invalid/my-link"), "public-api");
    });
    await t.test("public service remains functional when private access stops", () => {
      docker("stop", "--time", "1", gateway, vpn);
      assert.equal(request(proxy, "https://beepclick.app/app"), "public-web");
      denied(proxy, "https://dashboard.beepclick.app/api/auth/session");
    });
  } finally {
    for (const name of containers.reverse()) spawnSync("docker", ["rm", "-f", "-v", name], { stdio: "ignore", timeout: 15_000 });
    for (const name of networks.reverse()) spawnSync("docker", ["network", "rm", name], { stdio: "ignore", timeout: 15_000 });
    rmSync(temp, { recursive: true, force: true });
  }
});
