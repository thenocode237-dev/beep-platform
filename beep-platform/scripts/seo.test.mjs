// Run after npm run build:web. Checks the actual production HTML and metadata routes.
import test, { before, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const origin = new URL(process.env.BEEP_PUBLIC_URL || "https://beepclick.app").origin;
let core, server, base;
let logs = "";
const listen = (server) => new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const close = (server) => new Promise((resolve) => server.close(resolve));

before(async () => {
  core = createServer((req, res) => {
    const url = new URL(req.url, "http://localhost");
    res.setHeader("Content-Type", "application/json");
    if (url.pathname === "/v1/content") {
      const locale = url.searchParams.get("locale");
      const slug = locale === "en" ? "share-better" : "bien-partager";
      const items = url.searchParams.has("slug") && url.searchParams.get("slug") !== slug ? [] : [{
        kind: "blog", slug, locale, title: locale === "en" ? "Share better" : "Bien partager",
        body: "## BeepClick\n\nDes liens pour vos campagnes.", version: 1, published_at: "2026-10-20T10:00:00Z",
      }];
      res.end(JSON.stringify({ items }));
    } else {
      res.statusCode = 401;
      res.end("{}");
    }
  });
  await listen(core);
  const probe = createServer();
  await listen(probe);
  const port = probe.address().port;
  await close(probe);
  // NextURL normalizes loopback IPs to localhost; use that host for locale rewrites.
  base = `http://localhost:${port}`;
  server = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "beep/web", "-H", "localhost", "-p", String(port)], {
    cwd: root,
    env: { ...process.env, BEEP_PUBLIC_URL: origin, CORE_INTERNAL_URL: `http://127.0.0.1:${core.address().port}/v1`, NEXT_TELEMETRY_DISABLED: "1" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  for (const stream of [server.stdout, server.stderr]) stream.on("data", (chunk) => { logs = (logs + chunk).slice(-4000); });
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) throw new Error(`Next.js exited: ${logs}`);
    try {
      if ((await fetch(`${base}/robots.txt`, { signal: AbortSignal.timeout(2000) })).ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Next.js did not start: ${logs}`);
});

after(async () => {
  if (server && server.exitCode === null) {
    const stopped = new Promise((resolve) => server.once("exit", resolve));
    server.kill();
    await stopped;
  }
  if (core) await close(core);
});

async function html(path) {
  const response = await fetch(base + path, { redirect: "manual", headers: { "User-Agent": "Googlebot" } });
  assert.equal(response.status, 200, `${path}: ${response.headers.get("location")}`);
  return response.text();
}

test("FR/EN pages have distinct titles and page-specific canonical/hreflang URLs", async () => {
  for (const [path, title] of [["/features", "Fonctionnalités"], ["/en/features", "Features"], ["/docs", "Documentation"], ["/en/docs", "documentation"]]) {
    const body = await html(path + "?utm_source=seo-test");
    assert.ok(body.includes(`<link rel="canonical" href="${origin}${path}"`), path);
    assert.ok(body.includes(`<link rel="alternate" hrefLang="fr" href="${origin}${path.replace(/^\/en/, "")}"`), path);
    assert.match(body, new RegExp(`<title>[^<]*${title}`));
    assert.ok(body.includes('property="og:url"'));
    assert.ok(body.includes('name="twitter:card"'));
  }
});

test("robots announces sitemap; sitemap lists public pages and published articles only", async () => {
  const robots = await (await fetch(base + "/robots.txt")).text();
  assert.ok(robots.includes(`Sitemap: ${origin}/sitemap.xml`));
  const sitemap = await (await fetch(base + "/sitemap.xml")).text();
  for (const path of ["/features", "/en/features", "/blog/bien-partager", "/en/blog/share-better"]) assert.ok(sitemap.includes(`<loc>${origin}${path}</loc>`), path);
  assert.doesNotMatch(sitemap, /<loc>[^<]*\/(login|app|signup|report)(\/|<)/);
});

test("account and authentication routes carry noindex on both locales", async () => {
  for (const path of ["/login", "/en/signup", "/reset-password", "/app", "/en/app", "/report"]) {
    const response = await fetch(base + path, { redirect: "manual" });
    assert.equal(response.headers.get("x-robots-tag"), "noindex, nofollow", path);
    await response.text();
  }
});

test("articles use their own metadata and unknown slugs return 404", async () => {
  const body = await html("/blog/bien-partager");
  assert.match(body, /<title>Bien partager \| BeepClick<\/title>/);
  assert.ok(body.includes(`<link rel="canonical" href="${origin}/blog/bien-partager"`));
  assert.ok(body.includes('property="og:type" content="article"'));
  assert.doesNotMatch(body, /<link rel="alternate"/);
  const missing = await fetch(base + "/blog/missing");
  assert.equal(missing.status, 404);
  await missing.text();
});

test("French prefixes redirect permanently and home exposes structured data", async () => {
  const response = await fetch(base + "/fr/features?source=test", { redirect: "manual" });
  assert.equal(response.status, 308);
  const destination = new URL(response.headers.get("location"), base);
  assert.equal(destination.pathname, "/features");
  assert.equal(destination.search, "?source=test");
  const body = await html("/");
  assert.match(body, /type="application\/ld\+json"/);
  assert.match(body, /"@type":"WebSite"/);
});
