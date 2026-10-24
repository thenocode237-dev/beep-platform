// Test de charge k6 du moteur de redirection (specs §9 : palier 1 000 req/s en phase 1, budget p95 < 50 ms).
// Débit à arrivée constante : la latence mesurée n'est pas masquée par un ralentissement du générateur.
// Les requêtes k6 sont reconnues comme robots : elles ne faussent pas les statistiques.
//   RATE (req/s, défaut 200), DURATION (défaut 30s), TARGET (défaut http://proxy), HOST (défaut beep.localhost), SLUG (défaut demo)
import http from "k6/http";
import { check } from "k6";

const RATE = Number(__ENV.RATE || 200);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    redirect: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: Math.max(20, Math.ceil(RATE / 10)),
      maxVUs: Math.max(100, RATE),
    },
  },
  thresholds: {
    "http_req_duration{name:redirect}": ["p(95)<50", "p(99)<150"],
    "http_req_failed{name:redirect}": ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

const target = `${__ENV.TARGET || "http://proxy"}/${__ENV.SLUG || "demo"}`;
const params = { redirects: 0, headers: { Host: __ENV.HOST || "beep.localhost" }, tags: { name: "redirect" }, responseCallback: http.expectedStatuses(302) };

export default function () {
  const res = http.get(target, params);
  check(res, { "302 vers la destination": (r) => r.status === 302 && !!r.headers.Location });
}
