import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  apiOrigin,
  destination,
  linkRequest,
} from "../extensions/chrome/api.mjs";
test("extension rejects credentials, insecure production endpoints and executable destinations", () => {
  for (const url of [
    "http://example.org",
    "https://user:pass@example.org",
    "https://example.org/v1",
    "https://example.org/?key=abc",
  ])
    assert.throws(() => apiOrigin(url));
  assert.equal(apiOrigin("http://beep.localhost"), "http://beep.localhost");
  assert.throws(() => destination("javascript:alert(1)"));
  assert.throws(() => destination("file:///etc/passwd"));
});
test("API keys stay out of URLs and cookies, mutations are idempotent", () => {
  const key = "sk_" + "a".repeat(32);
  const r = linkRequest({
    origin: "https://beep.click",
    key,
    url: "https://example.org",
    title: "Test",
    deeplink: true,
    idempotency: "request-1",
  });
  assert.equal(r.url, "https://beep.click/v1/links");
  assert.equal(r.options.credentials, "omit");
  assert.equal(r.options.redirect, "error");
  assert.equal(r.options.headers["Idempotency-Key"], "request-1");
  assert.equal(JSON.parse(r.options.body).rules.deeplink, true);
  assert.ok(!r.url.includes(key));
});
test("manifest requests no history, persistent tabs or injected content scripts", () => {
  const m = JSON.parse(
    readFileSync(
      new URL("../extensions/chrome/manifest.json", import.meta.url),
    ),
  );
  assert.equal(m.manifest_version, 3);
  assert.deepEqual(m.permissions, ["activeTab", "storage"]);
  assert.equal(m.content_scripts, undefined);
  assert.equal(m.externally_connectable, undefined);
});
