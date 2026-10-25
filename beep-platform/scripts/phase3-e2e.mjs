import { randomUUID } from "node:crypto";
import vm from "node:vm";
import { readFileSync } from "node:fs";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { parse } from "yaml";
const adminContract = parse(
  readFileSync(
    new URL("../contracts/beep-admin.openapi.yaml", import.meta.url),
    "utf8",
  ),
);
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: "adminContract", components: adminContract.components });
const developerSchema = ajv.compile({
  $ref: "adminContract#/components/schemas/DeveloperWorkspace",
});
export async function phase3({
  site,
  bo,
  visitor,
  step,
  totp,
  staffSecret,
  workspaceId,
  bio,
  sleep,
}) {
  let r;
  if (process.env.E2E_BILLING_SANDBOX === "1") {
    r = await site.post("/v1/billing/checkout", {
      plan: "elite",
      cycle: "monthly",
      method: "mtn_momo",
      phone: "237670123456",
      country: "CM",
    });
    step(
      "phase 3: Elite sandbox checkout",
      r.status === 202,
      r.text.slice(0, 180),
    );
    if (r.json?.payment?.id) {
      const paid = await site.get(`/v1/billing/payments/${r.json.payment.id}`);
      step(
        "phase 3: Elite sandbox settlement",
        paid.status === 200 && paid.json?.status === "succeeded",
      );
    }
    r = await site.get("/v1/workspace");
    step(
      "phase 3: paid Elite enables monthly quotas and API",
      r.status === 200 &&
        r.json?.plan?.code === "elite" &&
        r.json?.entitlements?.["api.rpm"] === 600 &&
        r.json?.entitlements?.["features.conversion"] === 1 &&
        r.json?.quotas?.find((q) => q.resource === "links")?.period ===
          "monthly",
    );
  }
  r = await bo.put(`/api/workspaces/${workspaceId}/entitlements`, {
    overrides: {
      "api.rpm": 600,
      "features.conversion": 1,
      "features.webhooks": 1,
      "features.retargeting": 1,
      "events.monthly_max": 100,
      "features.bio_badge_removable": 1,
    },
    reason: "phase3 local recipe",
    totp: totp(staffSecret),
  });
  step(
    "phase 3: effective rights published",
    r.status === 200,
    r.text.slice(0, 150),
  );
  if (bio?.slug) {
    await sleep(250);
    r = await visitor.get("/" + bio.slug);
    step(
      "phase 3: entitled bio has no Beep badge",
      r.status === 200 && !r.text.includes("Propulsé par"),
    );
  }
  r = await site.post("/v1/developer/keys", {
    name: "e2e server",
    publicKey: false,
    scopes: ["read", "write", "events"],
  });
  const server = r.json;
  step(
    "phase 3: server key issued once",
    r.status === 201 && server?.token?.startsWith("sk_"),
  );
  r = await site.post("/v1/developer/keys", {
    name: "e2e read",
    publicKey: false,
    scopes: ["read"],
  });
  const read = r.json;
  r = await site.post("/v1/developer/keys", {
    name: "e2e browser",
    publicKey: true,
    scopes: ["events"],
    origins: ["https://merchant.example"],
  });
  const browser = r.json;
  step(
    "phase 3: public key restricted to origins",
    r.status === 201 && browser?.token?.startsWith("pk_"),
  );
  if (!server?.token || !read?.token || !browser?.token) return;
  const auth = { Authorization: `Bearer ${server.token}` };
  r = await visitor.get("/v1/links", { Authorization: `Bearer ${read.token}` });
  step("phase 3: bearer read", r.status === 200);
  r = await visitor.request(
    "POST",
    "/v1/links",
    { destination: "https://merchant.example/denied" },
    { Authorization: `Bearer ${read.token}` },
  );
  step("phase 3: read scope cannot mutate", r.status === 403);
  for (const path of ["/v1/auth/me", "/v1/developer", "/v1/billing"]) {
    r = await visitor.get(path, auth);
    step(`phase 3: server key denied ${path}`, r.status === 403);
  }
  for (const path of ["/v1/qr-codes", "/v1/bio-pages", "/v1/domains"]) {
    r = await visitor.get(path, auth);
    step(`phase 3: public resources ${path}`, r.status === 200);
  }
  const idem = { ...auth, "Idempotency-Key": randomUUID() },
    body = { destination: "https://merchant.example/thanks", title: "Phase 3" };
  r = await visitor.request("POST", "/v1/links", body, idem);
  const link = r.json;
  step(
    "phase 3: idempotent link creation",
    r.status === 201,
    r.text.slice(0, 160),
  );
  r = await visitor.request("POST", "/v1/links", body, idem);
  step(
    "phase 3: retry returns identical link",
    r.status === 201 &&
      r.json?.id === link?.id &&
      r.headers.get("idempotency-replayed") === "true",
    r.text.slice(0, 150),
  );
  r = await visitor.request(
    "POST",
    "/v1/links",
    { destination: "https://merchant.example/different" },
    idem,
  );
  step("phase 3: idempotency payload conflict", r.status === 409);
  if (!link?.id) return;
  r = await site.put(`/v1/conversions/links/${link.id}`, {
    enabled: true,
    model: "last",
    windowDays: 30,
    pixels: {},
  });
  step("phase 3: per-link tracking enabled", r.status === 200);
  r = await visitor.get(`/${link.slug}`);
  const location = r.headers.get("location");
  let cid;
  try {
    cid = new URL(location).searchParams.get("beep_cid");
  } catch {}
  step("phase 3: redirect carries signed click id", r.status === 302 && !!cid);
  await retargeting({ site, visitor, step });
  r = await site.post("/v1/webhooks", {
    url: "https://203.0.113.2/beep-recipe",
    events: ["sale"],
  });
  const endpoint = r.json;
  step(
    "phase 3: encrypted webhook secret returned once",
    r.status === 201 && endpoint?.secret?.startsWith("whsec_"),
  );
  const event = {
    key: browser.token,
    eventId: randomUUID(),
    name: "pageview",
    page: "https://merchant.example/thanks?private=secret",
    consent: false,
  };
  r = await visitor.request("POST", "/_b/collect", event, {
    Origin: "https://attacker.example",
  });
  step("phase 3: wrong origin rejected", r.status === 403);
  r = await visitor.request(
    "POST",
    "/_b/collect",
    { ...event, eventId: randomUUID() },
    {
      Origin: "https://merchant.example",
      "User-Agent": "Googlebot/2.1 (+http://www.google.com/bot.html)",
    },
  );
  step(
    "phase 3: known browser robots do not count as pageviews",
    r.status === 202 && r.json?.accepted === false,
  );
  r = await visitor.request("POST", "/_b/collect", event, {
    Origin: "https://merchant.example",
  });
  step(
    "phase 3: anonymous pageview accepted with exact CORS",
    r.status === 202 &&
      r.headers.get("access-control-allow-origin") ===
        "https://merchant.example",
  );
  r = await visitor.request(
    "POST",
    "/_b/collect",
    {
      ...event,
      eventId: randomUUID(),
      name: "sale",
      value: 49,
      currency: "EUR",
    },
    { Origin: "https://merchant.example" },
  );
  step("phase 3: conversion requires consent", r.status === 403);
  const sale = {
    eventId: randomUUID(),
    name: "sale",
    visitor: "e2e-consented-visitor",
    cid,
    value: 49,
    currency: "EUR",
    consent: true,
    utm: { source: "recipe", campaign: "phase3" },
  };
  r = await visitor.request(
    "POST",
    "/v1/events",
    { ...sale, cid: "forged" },
    auth,
  );
  step("phase 3: forged receipt rejected", r.status === 400);
  r = await visitor.request("POST", "/v1/events", sale, auth);
  step(
    "phase 3: server sale accepted",
    r.status === 202 && r.json?.accepted === true,
    r.text.slice(0, 150),
  );
  r = await visitor.request("POST", "/v1/events", sale, auth);
  step(
    "phase 3: duplicate event does not count twice",
    r.status === 202 && r.json?.accepted === false,
  );
  r = await visitor.request(
    "POST",
    "/v1/events",
    { ...sale, eventId: randomUUID(), name: "lead", value: 0, currency: null },
    auth,
  );
  step("phase 3: lead accepted", r.status === 202);
  r = await site.get("/v1/conversions/analytics");
  const funnel = r.json?.funnel?.find((x) => x.linkId === link.id);
  step(
    "phase 3: attribution funnel and currency revenue",
    r.status === 200 &&
      Number(funnel?.sales) === 1 &&
      Number(funnel?.leads) === 1 &&
      r.json?.revenue?.some(
        (x) =>
          x.linkId === link.id &&
          x.currency === "EUR" &&
          Number(x.revenue) === 49,
      ),
    r.text.slice(0, 250),
  );
  r = await site.get("/v1/conversions/analytics?days=36500");
  step(
    "phase 3: all-time anonymous totals available",
    r.status === 200 &&
      r.json?.revenue?.some(
        (x) => x.linkId === link.id && Number(x.revenue) === 49,
      ),
  );
  r = await site.get("/v1/developer");
  step(
    "phase 3: key and webhook secrets absent from overview",
    r.status === 200 &&
      !r.text.includes(server.token) &&
      !r.text.includes(endpoint?.secret ?? "never") &&
      !r.text.includes("token_hash"),
  );
  step(
    "phase 3: durable webhook outbox",
    r.json?.deliveries?.some((d) => d.event === "sale"),
  );
  r = await bo.get(`/api/developer/workspaces/${workspaceId}`);
  step(
    "phase 3: core and gateway conform to developer contract",
    r.status === 200 && developerSchema(r.json),
    JSON.stringify(developerSchema.errors),
  );
  r = await site.get(
    "/v1/conversions/stream?after=" +
      encodeURIComponent(new Date(Date.now() - 60000).toISOString()),
  );
  const streamEvents = r.text
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => {
      try {
        return JSON.parse(line.slice(5));
      } catch {
        return null;
      }
    });
  const visitorHash = streamEvents.find((e) => e?.name === "sale")?.visitor;
  step(
    "phase 3: live event stream resumes with pseudonymous identity",
    r.status === 200 &&
      typeof visitorHash === "string" &&
      /^[0-9a-f]{64}$/.test(visitorHash) &&
      !r.text.includes("e2e-consented-visitor"),
  );
  if (visitorHash) {
    r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
      action: "visitor.export",
      id: visitorHash,
      reason: "visitor data request",
      totp: totp(staffSecret),
    });
    step(
      "phase 3: visitor export",
      r.status === 200 && r.json?.events?.length === 2,
    );
    r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
      action: "visitor.erase",
      id: visitorHash,
      reason: "visitor deletion request",
      totp: totp(staffSecret),
    });
    step("phase 3: visitor erasure", r.status === 200);
    r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
      action: "visitor.export",
      id: visitorHash,
      reason: "verify visitor erasure",
      totp: totp(staffSecret),
    });
    step(
      "phase 3: erased visitor no longer exported",
      r.status === 200 && r.json?.events?.length === 0,
    );
  }
  r = await bo.get("/api/developer/monitor");
  step(
    "phase 3: staff pipeline and API dashboard",
    r.status === 200 && Array.isArray(r.json?.rejections),
  );
  r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
    action: "revoke",
    id: read.id,
    reason: "local recipe",
    totp: "000000",
  });
  step("phase 3: staff revocation requires step-up", r.status === 403);
  r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
    action: "revoke",
    id: read.id,
    reason: "local recipe",
    totp: totp(staffSecret),
  });
  step("phase 3: staff revokes API key", r.status === 200);
  r = await visitor.get("/v1/links", { Authorization: `Bearer ${read.token}` });
  step("phase 3: revoked key immediately rejected", r.status === 401);
  r = await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
    action: "limits",
    rpm: 2,
    events: 100,
    reason: "rate-limit recipe",
    totp: totp(staffSecret),
  });
  step("phase 3: adjustable API limits", r.status === 200);
  for (let attempt = 0; attempt < 3; attempt++) {
    r = await visitor.get("/v1/links", auth);
    if (r.status === 429) break;
  }
  step(
    "phase 3: shared workspace rate limit enforced",
    r.status === 429 && r.headers.get("retry-after") === "60",
  );
  await bo.post(`/api/developer/workspaces/${workspaceId}/actions`, {
    action: "limits",
    rpm: 600,
    events: 100,
    reason: "restore recipe rate",
    totp: totp(staffSecret),
  });
  for (const path of [
    "/docs",
    "/en/docs",
    "/app/developer",
    "/app/developer?days=36500",
    "/app/menu",
  ]) {
    r = await site.get(path);
    step(`phase 3: client page ${path}`, r.status === 200);
  }
  r = await bo.get("/developer");
  step("phase 3: staff developer page", r.status === 200);
  r = await visitor.get("/v1/openapi.json");
  step(
    "phase 3: published public OpenAPI",
    r.status === 200 && r.json?.openapi === "3.1.0",
  );
  r = await visitor.get("/beep.js");
  step(
    "phase 3: tracking script served",
    r.status === 200 && r.text.includes("sendBeacon"),
  );
  await site.del(`/v1/webhooks/${endpoint.id}`);
  await site.del(`/v1/developer/keys/${server.id}`);
  await site.del(`/v1/developer/keys/${browser.id}`);
}

async function retargeting({ site, visitor, step }) {
  let r = await site.post("/v1/links", {
    destination: "https://merchant.example/pixel",
    domain: "main",
  });
  const link = r.json;
  step("phase 3: retargeting fixture created", r.status === 201);
  if (!link?.id) return;
  r = await site.put(`/v1/conversions/links/${link.id}`, {
    enabled: true,
    model: "last",
    windowDays: 30,
    pixels: { meta: "123456", google: "AW-123456", tiktok: "TESTPIXEL123" },
  });
  step("phase 3: advertising pixels configured", r.status === 200);
  r = await visitor.get("/" + link.slug);
  let target;
  try {
    target = new URL(r.headers.get("location"));
  } catch {}
  step(
    "phase 3: pixels use a signed consent interstitial",
    r.status === 302 && target?.pathname === "/_b/retarget",
  );
  if (!target) return;
  const path = target.pathname + target.search,
    form = new URLSearchParams(target.searchParams);
  form.set("consent", "yes");
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    Origin: target.origin,
  };
  r = await visitor.get(path);
  step(
    "phase 3: no third-party script before consent",
    r.status === 200 &&
      !r.text.includes("<script") &&
      r.text.includes("Continuer sans accepter"),
  );
  r = await visitor.request("POST", "/_b/retarget", form.toString(), {
    ...headers,
    Origin: "https://untrusted.example",
  });
  step("phase 3: cross-site consent submission rejected", r.status === 403);
  r = await visitor.request("POST", "/_b/retarget", form.toString(), {
    ...headers,
    "Sec-GPC": "1",
  });
  step(
    "phase 3: GPC header bypasses all advertising scripts",
    r.status === 302 &&
      r.headers.get("location")?.startsWith("https://merchant.example/pixel"),
  );
  r = await visitor.request("POST", "/_b/retarget", form.toString(), headers);
  step(
    "phase 3: explicit consent renders provider code with scoped CSP",
    r.status === 200 &&
      r.text.includes("connect.facebook.net") &&
      r.text.includes("analytics.tiktok.com") &&
      r.headers
        .get("content-security-policy")
        ?.includes("googleads.g.doubleclick.net"),
  );
  const code = /<script nonce='[^']+'>([\s\S]*?)<\/script>/.exec(r.text)?.[1];
  let redirected;
  if (code)
    vm.runInNewContext(code, {
      navigator: { globalPrivacyControl: true },
      location: {
        replace(url) {
          redirected = url;
        },
      },
      document: {
        createElement() {
          throw Error("GPC must prevent SDK creation");
        },
      },
      setTimeout() {
        throw Error("GPC must navigate immediately");
      },
    });
  step(
    "phase 3: browser GPC blocks SDK execution",
    redirected?.startsWith("https://merchant.example/pixel"),
  );
  const tampered = new URL(target);
  tampered.searchParams.set("sig", "tampered");
  r = await visitor.get(tampered.pathname + tampered.search);
  step("phase 3: modified retarget receipt rejected", r.status === 400);
  await site.put(`/v1/conversions/links/${link.id}`, {
    enabled: false,
    model: "last",
    windowDays: 30,
    pixels: {},
  });
  r = await visitor.request("POST", "/_b/retarget", form.toString(), headers);
  step(
    "phase 3: disabled tracking still reaches the destination",
    r.status === 302 &&
      r.headers.get("location")?.startsWith("https://merchant.example/pixel"),
  );
}
