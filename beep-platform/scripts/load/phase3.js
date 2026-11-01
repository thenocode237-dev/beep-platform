import http from "k6/http";
import { check } from "k6";
const keys = JSON.parse(__ENV.COLLECT_KEYS || "[]");
if (!keys.length)
  throw new Error(
    "COLLECT_KEYS must be a JSON array of public keys for dedicated load-test workspaces",
  );
const target = __ENV.TARGET || "http://proxy",
  host = __ENV.HOST || "beep.localhost",
  origin = __ENV.COLLECT_ORIGIN || "https://load.example";
export const options = {
  scenarios: {
    redirects: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.REDIRECT_RATE || 5000),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: 100,
      maxVUs: 1000,
      exec: "redirect",
    },
    ingestion: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.EVENT_RATE || 2000),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: 100,
      maxVUs: 1000,
      exec: "ingest",
    },
  },
  thresholds: {
    "http_req_duration{scenario:redirects}": ["p(95)<50"],
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    dropped_iterations: ["count==0"],
  },
};
export function redirect() {
  const r = http.get(target + "/" + (__ENV.SLUG || "demo"), {
    headers: { Host: host, "User-Agent": "Beepclick phase3 load browser" },
    redirects: 0,
  });
  check(r, { "redirect 302": (r) => r.status === 302 });
}
export function ingest() {
  const key = keys[(__VU - 1) % keys.length];
  const bits = (
    Date.now().toString(16).padStart(12, "0") +
    __VU.toString(16).padStart(8, "0") +
    __ITER.toString(16).padStart(12, "0")
  ).slice(-32);
  const uuid =
    bits.slice(0, 8) +
    "-" +
    bits.slice(8, 12) +
    "-" +
    bits.slice(12, 16) +
    "-" +
    bits.slice(16, 20) +
    "-" +
    bits.slice(20);
  const body = JSON.stringify({
    key,
    eventId: uuid,
    name: "pageview",
    page: origin + "/load",
    consent: false,
  });
  const r = http.post(target + "/_b/collect", body, {
    headers: {
      Host: host,
      Origin: origin,
      "Content-Type": "text/plain",
      "User-Agent":
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    },
  });
  check(r, {
    "event accepted": (r) => r.status === 202 && r.json("accepted") === true,
  });
}
