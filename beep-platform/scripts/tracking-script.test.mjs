import { test } from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import fs from "node:fs";
import { webcrypto } from "node:crypto";
const source = fs.readFileSync(
  new URL(
    "../beep/core/adapter-in-http/src/main/resources/beep.js",
    import.meta.url,
  ),
  "utf8",
);
function browser({ cookieless = false, gpc = false } = {}) {
  const payloads = [],
    writes = [],
    cookies = new Map();
  const document = {
    currentScript: {
      src: "https://beep.example/beep.js",
      getAttribute(n) {
        return n === "data-key"
          ? "pk_public"
          : n === "data-cookieless"
            ? String(cookieless)
            : null;
      },
    },
    get cookie() {
      return [...cookies].map(([k, v]) => `${k}=${v}`).join("; ");
    },
    set cookie(v) {
      writes.push(v);
      const pair = v.split(";")[0];
      const [k, ...rest] = pair.split("=");
      if (v.includes("Max-Age=0")) cookies.delete(k);
      else cookies.set(k, rest.join("="));
    },
  };
  const window = {
    crypto: webcrypto,
    location: {
      href: "https://shop.example/offer?beep_cid=signed-click&email=private",
      origin: "https://shop.example",
      pathname: "/offer",
      protocol: "https:",
    },
    navigator: {
      globalPrivacyControl: gpc,
      sendBeacon(url, body) {
        payloads.push({ url, body });
        return true;
      },
    },
  };
  vm.runInNewContext(source, {
    window,
    document,
    URL,
    Blob,
    Uint8Array,
    Array,
  });
  return {
    window,
    payloads,
    writes,
    cookies,
    async events() {
      return Promise.all(
        payloads.map(async (p) => JSON.parse(await p.body.text())),
      );
    },
  };
}
test("default pageview writes no cookie, carries no visitor, CID or query", async () => {
  const b = browser();
  const [event] = await b.events();
  assert.equal(b.writes.length, 0);
  assert.equal(event.name, "pageview");
  assert.equal(event.consent, false);
  assert.equal(event.visitor, null);
  assert.equal(event.cid, null);
  assert.equal(event.page, "https://shop.example/offer");
  b.window.beep("track", "sale", { value: 49, currency: "EUR" });
  assert.equal(b.payloads.length, 1);
});
test("explicit consent permits conversion and withdrawal removes all identifiers", async () => {
  const b = browser();
  b.window.beep("consent", true);
  b.window.beep("track", "sale", { value: 49, currency: "EUR" });
  let events = await b.events();
  assert.equal(events[1].cid, "signed-click");
  assert.equal(events[1].value, 49);
  assert.ok(events[1].visitor);
  assert.equal(b.cookies.size, 2);
  b.window.beep("consent", false);
  assert.equal(b.cookies.size, 0);
  b.window.beep("track", "sale", { value: 49, currency: "EUR" });
  assert.equal(b.payloads.length, 2);
});
for (const options of [{ cookieless: true }, { gpc: true }])
  test(
    "cookieless override and GPC prevent consented tracking " +
      JSON.stringify(options),
    async () => {
      const b = browser(options);
      b.window.beep("consent", true);
      b.window.beep("track", "sale", { value: 49, currency: "EUR" });
      assert.equal(b.cookies.size, 0);
      assert.equal(b.payloads.length, 1);
    },
  );
