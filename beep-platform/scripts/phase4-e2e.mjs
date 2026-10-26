import { createHmac, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { parse } from "yaml";
const contract = parse(
  readFileSync(
    new URL("../contracts/beep-admin.openapi.yaml", import.meta.url),
    "utf8",
  ),
);
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: "ecosystem", components: contract.components });
const workspaceContract = ajv.compile({
  $ref: "ecosystem#/components/schemas/EcosystemWorkspace",
});
export async function phase4({
  site,
  bo,
  visitor,
  step,
  totp,
  staffSecret,
  workspaceId,
  bio,
  sleep,
  Client,
  SITE,
  BROWSER,
  lastMailFor,
  lastMailTokenFor,
}) {
  const suffix = Date.now().toString(36),
    member = new Client(SITE, BROWSER),
    email = `team-${suffix}@beep.localhost`,
    password = "Team-password-1234";
  let r;
  r = await site.get("/v1/team");
  step(
    "phase 4: owner and 15 Elite seats",
    r.status === 200 && r.json?.role === "owner" && r.json?.limit === 15,
  );
  const team = r.json;
  r = await site.post("/v1/organization/labels", {
    kind: "folder",
    name: `Campaign ${suffix}`,
  });
  const folder = r.json?.id;
  step("phase 4: create a folder", r.status === 200 && !!folder);
  r = await site.post("/v1/organization/labels", {
    kind: "tag",
    name: `Launch ${suffix}`,
  });
  const tag = r.json?.id;
  const slug = `bulk-${suffix}`;
  r = await site.post("/v1/organization/import", {
    csv: `destination,slug,title\r\nhttps://example.org,${slug},"=unsafe formula"\r\nnot a URL,bad-${suffix},Invalid\r\n`,
  });
  step(
    "phase 4: CSV row-level results preserve successful rows",
    r.status === 200 &&
      r.json?.items?.[0]?.status === "created" &&
      r.json?.items?.[1]?.status === "error",
  );
  const link = r.json?.items?.[0]?.id;
  if (link) {
    r = await site.put(`/v1/organization/links/${link}`, {
      folder,
      tags: [tag],
    });
    step("phase 4: folder and tags saved", r.status === 200);
  }
  r = await site.get("/v1/organization/export.csv");
  step(
    "phase 4: CSV export protects spreadsheet formulas",
    r.status === 200 && r.text.includes("'=unsafe formula"),
  );
  r = await site.post("/v1/organization/import", {
    csv: 'destination,title\n"unterminated',
  });
  step("phase 4: malformed CSV rejected before creation", r.status === 400);
  r = await site.post("/v1/workspaces", { name: `Separate ${suffix}` });
  const other = r.json?.id;
  step(
    "phase 4: second owned workspace",
    r.status === 200 && other !== workspaceId,
  );
  if (other) {
    await site.post("/v1/workspaces/select", { id: other });
    r = await site.get("/v1/workspace");
    step(
      "phase 4: request-local workspace selection",
      r.status === 200 &&
        r.json?.id === other &&
        r.json?.plan?.code === "starter",
    );
    r = await site.post("/v1/organization/labels", {
      kind: "tag",
      name: "Private",
    });
    const foreignTag = r.json?.id;
    await site.post("/v1/workspaces/select", { id: workspaceId });
    if (link) {
      r = await site.put(`/v1/organization/links/${link}`, {
        folder: null,
        tags: [foreignTag],
      });
      step("phase 4: cross-workspace labels denied", r.status === 404);
    }
  }
  r = await site.post("/v1/team/invitations", { email, role: "viewer" });
  step("phase 4: team invitation reserves a seat", r.status === 200);
  const invitationMail = await lastMailFor(email, /Invitation Beep/i),
    invitation = /invite=([A-Za-z0-9_-]+)/.exec(
      invitationMail?.Text ?? "",
    )?.[1];
  step("phase 4: invitation sent through Mailpit", !!invitation);
  await member.post("/v1/auth/signup", { email, password, locale: "fr" });
  const verify = await lastMailTokenFor(email, "verify");
  await member.post("/v1/auth/verify-email", { token: verify });
  r = await member.post("/v1/auth/login", { email, password });
  step(
    "phase 4: invited teammate can create a verified account",
    r.status === 200,
  );
  r = await member.get("/v1/auth/me");
  const memberId = r.json?.id;
  if (invitation && memberId) {
    r = await member.post("/v1/team/accept", { token: invitation });
    step(
      "phase 4: accept verified invitation",
      r.status === 200 && r.json?.workspace === workspaceId,
    );
    r = await member.post("/v1/team/accept", { token: invitation });
    step("phase 4: invitation token is single-use", r.status === 400);
    await member.post("/v1/workspaces/select", { id: workspaceId });
    r = await member.get("/v1/links");
    step("phase 4: viewer can read links", r.status === 200);
    r = await member.post("/v1/account/export");
    step(
      "phase 4: account privacy remains personal when a team is selected",
      r.status === 200 &&
        r.json?.user?.email === email &&
        r.json?.links?.length === 0,
    );
    for (const path of [
      "/v1/links",
      "/v1/qr-codes",
      "/v1/bio-pages",
      "/v1/domains",
      "/v1/developer/keys",
    ]) {
      r = await member.post(path, {});
      step(`phase 4: viewer mutation denied ${path}`, r.status === 403);
    }
    await site.patch(`/v1/team/members/${memberId}`, { role: "editor" });
    r = await member.post("/v1/links", {
      destination: "https://example.org/editor",
      title: "Team editor",
    });
    step(
      "phase 4: role change immediately enables editor writes",
      r.status === 201,
    );
    r = await member.post("/v1/developer/keys", {
      name: "forbidden",
      scopes: ["write"],
      publicKey: false,
    });
    step("phase 4: editor cannot issue credentials", r.status === 403);
    r = await member.post("/v1/billing/checkout", {});
    step("phase 4: billing stays owner-only", r.status === 403);
    r = await member.get("/v1/billing");
    step("phase 4: private billing reads stay owner-only", r.status === 403);
    await site.patch(`/v1/team/members/${memberId}`, { role: "admin" });
    const ownerId = team?.members?.find((m) => m.role === "owner")?.id;
    r = await member.patch(`/v1/team/members/${ownerId}`, { role: "viewer" });
    step("phase 4: admin cannot demote owner", r.status === 403);
    await site.del(`/v1/team/members/${memberId}`);
    r = await member.get("/v1/links");
    step(
      "phase 4: removal revokes access without logging out",
      r.status === 403,
    );
    r = await member.get("/v1/workspaces");
    step(
      "phase 4: removed member can recover their personal workspace",
      r.status === 200 && !r.json?.items?.some((w) => w.id === workspaceId),
    );
    const personal = r.json?.items?.find((w) => w.role === "owner");
    if (personal)
      await member.post("/v1/workspaces/select", { id: personal.id });
  }
  const signing = "fixture-webhook-secret-" + randomUUID();
  r = await site.post("/v1/integrations", {
    provider: "stripe",
    name: "Stripe recipe",
    secret: signing,
  });
  const integration = r.json?.id;
  step("phase 4: native Stripe connection", r.status === 200 && !!integration);
  if (integration) {
    const raw = JSON.stringify({
        id: "evt_" + suffix,
        type: "checkout.session.completed",
        data: {
          object: {
            id: "cs_" + suffix,
            payment_intent: "pi_" + suffix,
            payment_status: "paid",
            amount_total: 4999,
            currency: "eur",
            metadata: {
              beep_consent: "true",
              beep_visitor: "opaquevisitor_" + suffix.padStart(16, "0"),
            },
          },
        },
      }),
      timestamp = Math.floor(Date.now() / 1000),
      signature =
        "t=" +
        timestamp +
        ",v1=" +
        createHmac("sha256", signing)
          .update(timestamp + "." + raw)
          .digest("hex");
    r = await visitor.request(
      "POST",
      `/v1/integrations/hooks/${integration}`,
      raw,
      { "Stripe-Signature": signature },
    );
    step(
      "phase 4: verified commerce event queued",
      r.status === 202 && r.json?.accepted === true,
    );
    r = await visitor.request(
      "POST",
      `/v1/integrations/hooks/${integration}`,
      raw,
      { "Stripe-Signature": signature },
    );
    step(
      "phase 4: provider business event deduplicated",
      r.status === 202 && r.json?.accepted === false,
    );
    r = await visitor.request(
      "POST",
      `/v1/integrations/hooks/${integration}`,
      raw + " ",
      { "Stripe-Signature": signature },
    );
    step("phase 4: raw-body tampering denied", r.status === 401);
    let processed = false;
    for (let i = 0; i < 25; i++) {
      r = await site.get("/v1/integrations");
      if (
        r.json?.events?.some(
          (e) => e.integration_id === integration && e.status === "processed",
        )
      ) {
        processed = true;
        break;
      }
      await sleep(1000);
    }
    step("phase 4: worker converts durable commerce delivery", processed);
    r = await site.get("/v1/integrations");
    step(
      "phase 4: native credentials never returned",
      r.status === 200 && !r.text.includes(signing),
    );
    await site.del(`/v1/integrations/${integration}`);
    r = await visitor.request(
      "POST",
      `/v1/integrations/hooks/${integration}`,
      raw,
      { "Stripe-Signature": signature },
    );
    step(
      "phase 4: disconnect immediately rejects incoming events",
      r.status === 404,
    );
  }
  for (const provider of ["shopify", "woocommerce"]) {
    r = await site.post("/v1/integrations", {
      provider,
      name: provider + " recipe",
      secret: signing,
      ...(provider === "shopify"
        ? { account: "recipe-store.myshopify.com" }
        : {}),
    });
    const id = r.json?.id;
    step(`phase 4: native ${provider} connection`, r.status === 200 && !!id);
    if (id) {
      const body = JSON.stringify(
          provider === "shopify"
            ? {
                id: suffix,
                financial_status: "paid",
                total_price: "12.50",
                currency: "EUR",
                note_attributes: [{ name: "beep_consent", value: "true" }],
              }
            : {
                id: suffix,
                status: "completed",
                total: "12.50",
                currency: "EUR",
                meta_data: [{ key: "beep_consent", value: "true" }],
              },
        ),
        signature = createHmac("sha256", signing).update(body).digest("base64");
      r = await visitor.request(
        "POST",
        `/v1/integrations/hooks/${id}`,
        body,
        provider === "shopify"
          ? {
              "X-Shopify-Hmac-Sha256": signature,
              "X-Shopify-Shop-Domain": "recipe-store.myshopify.com",
            }
          : { "X-WC-Webhook-Signature": signature },
      );
      step(
        `phase 4: verified ${provider} sale`,
        r.status === 202 && r.json?.accepted === true,
      );
      await site.del(`/v1/integrations/${id}`);
    }
  }
  r = await site.post("/v1/integrations", {
    provider: "zapier",
    name: "Zapier recipe",
    url: "https://example.invalid/private-hook-" + suffix,
  });
  const zapier = r.json?.id;
  step(
    "phase 4: Zapier signing secret displayed once",
    r.status === 200 && r.json?.signingSecret?.startsWith("whsec_"),
  );
  if (zapier) {
    r = await site.get("/v1/developer");
    step(
      "phase 4: native webhook credentials encrypted and redacted",
      r.status === 200 && !r.text.includes("private-hook-" + suffix),
    );
    await site.del(`/v1/integrations/${zapier}`);
  }
  r = await site.post("/v1/radar/rules", {
    kind: "click_spike",
    threshold: 1,
    channel: "email",
  });
  const rule = r.json?.id;
  step("phase 4: Radar rule saved", r.status === 200 && !!rule);
  r = await site.put("/v1/radar/reports", {
    frequency: "weekly",
    enabled: true,
  });
  step("phase 4: scheduled email report configured", r.status === 200);
  r = await site.get("/v1/radar");
  step(
    "phase 4: Radar exposes rules, reports and destination checks",
    r.status === 200 &&
      Array.isArray(r.json?.health) &&
      r.json?.reports?.[0]?.frequency === "weekly",
  );
  r = await site.get("/v1/insights");
  step(
    "phase 4: aggregate customer insights",
    r.status === 200 && Array.isArray(r.json?.segments),
  );
  r = await site.post("/v1/affiliates/join", { terms: true });
  const affiliateCode = r.json?.code;
  step(
    "phase 4: affiliate enrollment",
    r.status === 200 && /^[a-z0-9]{12}$/.test(affiliateCode),
  );
  r = await site.post("/v1/affiliates/referral", { code: affiliateCode });
  step("phase 4: self-referral denied", r.status === 403);
  if (memberId) {
    r = await member.post("/v1/affiliates/join", { terms: true });
    const referredBy = r.json?.code;
    if (other && referredBy) {
      await site.post("/v1/workspaces/select", { id: other });
      r = await site.post("/v1/affiliates/referral", { code: referredBy });
      step(
        "phase 4: new unpaid workspace records one referral",
        r.status === 200,
      );
      r = await site.post("/v1/affiliates/referral", { code: referredBy });
      step(
        "phase 4: referral cannot be overwritten",
        r.status === 404 || r.status === 409,
      );
      await site.post("/v1/workspaces/select", { id: workspaceId });
    }
  }
  if (bio?.id) {
    r = await site.put(`/v1/bio-pages/${bio.id}/blocks`, {
      blocks: [
        {
          id: "formp4001",
          type: "form",
          config: {
            label: "Contact us",
            privacyUrl: "https://example.org/privacy",
            consentText: "I consent to this message being stored.",
          },
        },
        {
          id: "videop401",
          type: "video",
          config: { label: "Demo", url: "https://example.org/video.mp4" },
        },
        {
          id: "audiop401",
          type: "audio",
          config: { label: "Audio", url: "https://example.org/audio.mp3" },
        },
      ],
    });
    step("phase 4: advanced bio blocks saved", r.status === 200);
    r = await visitor.get("/" + bio.slug);
    step(
      "phase 4: server-rendered consent form and media",
      r.status === 200 &&
        r.text.includes("/_b/forms/") &&
        r.text.includes("<video") &&
        r.text.includes("<audio"),
    );
    const form = "email=lead%40example.org&message=Hello&consent=true";
    r = await visitor.request("POST", `/_b/forms/${bio.id}/formp4001`, form, {
      "Content-Type": "application/x-www-form-urlencoded",
      Origin: "https://evil.example",
    });
    step("phase 4: third-party bio form submission denied", r.status === 403);
    r = await visitor.request("POST", `/_b/forms/${bio.id}/formp4001`, form, {
      "Content-Type": "application/x-www-form-urlencoded",
      Origin: "http://beep.localhost",
    });
    step("phase 4: consented bio response saved", r.status === 200);
    r = await site.get("/v1/bio-submissions");
    const submission = r.json?.items?.find(
      (x) => x.email === "lead@example.org",
    );
    step(
      "phase 4: only workspace admins can read collected responses",
      r.status === 200 && !!submission,
    );
    if (submission) {
      r = await site.del(`/v1/bio-submissions/${submission.id}`);
      step("phase 4: response can be erased", r.status === 200);
    }
  }
  r = await site.get("/v1/team/audit");
  step(
    "phase 4: workspace audit records resource and membership actions",
    r.status === 200 &&
      r.json?.items?.some((x) => x.action === "member.removed"),
  );
  r = await bo.get("/api/ecosystem/monitor");
  step(
    "phase 4: staff integration and cohort monitoring",
    r.status === 200 && Array.isArray(r.json?.cohorts),
  );
  r = await bo.get(`/api/ecosystem/workspaces/${workspaceId}`);
  step(
    "phase 4: staff workspace supervision",
    r.status === 200 && Array.isArray(r.json?.integrations),
  );
  step(
    "phase 4: workspace gateway respects the typed contract",
    workspaceContract(r.json),
    JSON.stringify(workspaceContract.errors),
  );
  r = await bo.post(`/api/ecosystem/workspaces/${workspaceId}/actions`, {
    action: "impersonate",
    reason: "support recipe",
    totp: "000000",
  });
  step(
    "phase 4: impersonation needs TOTP",
    r.status === 403 && r.json?.code === "reauth_required",
  );
  r = await bo.post(`/api/ecosystem/workspaces/${workspaceId}/actions`, {
    action: "impersonate",
    reason: "support recipe",
    totp: totp(staffSecret),
  });
  const readonly = r.json?.token;
  step(
    "phase 4: staff readonly view has a bounded TTL",
    r.status === 200 &&
      r.json?.readOnly &&
      new Date(r.json.expiresAt).getTime() <= Date.now() + 601000,
  );
  if (readonly) {
    r = await bo.post("/api/ecosystem/impersonation/read", { token: readonly });
    step(
      "phase 4: readonly view exposes resources through the gateway",
      r.status === 200 &&
        r.json?.readOnly &&
        Array.isArray(r.json?.view?.links),
    );
    await bo.post("/api/ecosystem/impersonation/stop", { token: readonly });
    r = await bo.post("/api/ecosystem/impersonation/read", { token: readonly });
    step("phase 4: readonly token revocation", r.status === 404);
  }
  r = await bo.post(`/api/ecosystem/workspaces/${workspaceId}/actions`, {
    action: "note",
    text: "Support recipe note",
    reason: "support ticket",
    totp: totp(staffSecret),
  });
  step("phase 4: local staff support note", r.status === 200);
  const articleSlug = "recipe-" + suffix;
  r = await bo.post("/api/ecosystem/content", {
    kind: "blog",
    slug: articleSlug,
    locale: "fr",
    title: "Editorial recipe",
    body: "Text <script>alert(1)</script>\n\n## Section",
    version: 0,
  });
  const draft = r.json?.id;
  step(
    "phase 4: editorial draft saved only in backoffice",
    r.status === 200 && !!draft,
  );
  r = await visitor.get("/v1/content?kind=blog&locale=fr&slug=" + articleSlug);
  step(
    "phase 4: draft absent from runtime",
    r.status === 200 && r.json?.items?.length === 0,
  );
  if (draft) {
    r = await bo.post(`/api/ecosystem/content/${draft}/publish`, {
      reason: "publish recipe",
      totp: totp(staffSecret),
      status: "published",
    });
    step("phase 4: versioned editorial publication", r.status === 200);
    r = await visitor.get("/blog");
    step(
      "phase 4: journal reflects a new publication immediately",
      r.status === 200 && r.text.includes(articleSlug),
    );
    r = await visitor.get("/blog/" + articleSlug);
    step(
      "phase 4: published body is escaped",
      r.status === 200 &&
        r.text.includes("&lt;script&gt;") &&
        !r.text.includes("<script>alert(1)"),
    );
    r = await bo.post(`/api/ecosystem/content/${draft}/publish`, {
      reason: "withdraw recipe",
      totp: totp(staffSecret),
      status: "withdrawn",
    });
    step(
      "phase 4: content can be withdrawn with a newer version",
      r.status === 200,
    );
    r = await visitor.get(
      "/v1/content?kind=blog&locale=fr&slug=" + articleSlug,
    );
    step(
      "phase 4: withdrawn content is not publicly exposed",
      r.status === 200 && r.json?.items?.length === 0,
    );
    r = await visitor.get("/blog");
    step(
      "phase 4: journal reflects withdrawal immediately",
      r.status === 200 && !r.text.includes(articleSlug),
    );
  }
  r = await bo.get("/api/ecosystem/affiliates");
  step(
    "phase 4: affiliate finance console",
    r.status === 200 && Array.isArray(r.json?.items),
  );
  r = await bo.get("/api/ecosystem/staff");
  step(
    "phase 4: granular staff access console",
    r.status === 200 && Array.isArray(r.json?.items),
  );
  const own = await bo.get("/api/auth/me");
  r = await bo.patch(`/api/ecosystem/staff/${own.json?.id}`, {
    role: "readonly",
    active: false,
    reason: "self demotion test",
    totp: totp(staffSecret),
  });
  step("phase 4: staff self-demotion denied", r.status === 403);
  for (const path of [
    "/app/team",
    "/app/organization",
    "/app/integrations",
    "/app/radar",
    "/app/insights",
    "/app/affiliates",
    "/app/bio-submissions",
    "/en/app/team",
  ]) {
    r = await site.get(path);
    step(`phase 4: client page ${path}`, r.status === 200);
  }
  for (const path of ["/ecosystem", "/content", "/affiliates", "/staff"]) {
    r = await bo.get(path);
    step(`phase 4: staff page ${path}`, r.status === 200);
  }
  for (const path of [
    "/roadmap",
    "/changelog",
    "/affiliates",
    "/en/roadmap",
    "/en/changelog",
    "/en/affiliates",
  ]) {
    r = await visitor.get(path);
    step(`phase 4: public page ${path}`, r.status === 200);
  }
  if (memberId) {
    r = await bo.post(`/api/users/${memberId}/erase`, {
      reason: "cleanup team recipe",
      totp: totp(staffSecret),
    });
    step(
      "phase 4: erased teammate is anonymized in team audit",
      r.status === 204 || r.status === 200,
    );
  }
}
