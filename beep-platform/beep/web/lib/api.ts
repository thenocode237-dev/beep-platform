// Appels vers beep-core. Navigateur : chemin relatif /v1 (même hôte, routé par Caddy ; cookie de session host-only).
// Serveur (SSR) : CORE_INTERNAL_URL (réseau Docker), voir lib/core.ts.

export type ApiResult<T = Record<string, unknown>> = { ok: boolean; status: number; code?: string; data?: T };

export async function send<T = Record<string, unknown>>(method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE", path: string, body?: unknown): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`/v1${path}`, {
      method,
      credentials: "include",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const json = res.headers.get("content-type")?.includes("json") ? await res.json().catch(() => undefined) : undefined;
    return { ok: res.ok, status: res.status, code: json?.code, data: json };
  } catch {
    return { ok: false, status: 0, code: "network" };
  }
}

export const post = <T = Record<string, unknown>>(path: string, body?: unknown) => send<T>("POST", path, body);

// ---- Types de l'API /v1 ----
export type Point = { bucket: string; clicks: number; uniques: number };
export type Quota = { resource: "links" | "qr" | "bio"; period: "stock" | "monthly"; used: number; limit: number; level: "ok" | "warning" | "reached" };
export type WorkspaceView = {
  id: string; name: string; status: string; plan: { code: string; name: string; priceXaf: number }; entitlements: Record<string, number>;
  quotas: Quota[]; clicks30d: number; daily: Point[]; createdAt: string;
};
export type LinkView = {
  id: string; slug: string; shortUrl: string; destination: string; title: string | null; status: "active" | "disabled"; createdAt: string; updatedAt: string;
  rules: LinkRules; passwordProtected: boolean;
  clicks30d: number | null; domainId: string | null; domain: string | null;
};
export type LinksPage = { items: LinkView[]; nextCursor: string | null };
export type Breakdown = { key: string; clicks: number };
export type Stats = {
  resource: string; from: string; to: string; days: number; maxDays: number; granularity: "hour" | "day"; clicks: number; uniques: number;
  secondaryClicks: number | null; series: Point[]; breakdowns: Record<string, Breakdown[]>; advanced: boolean; exportAllowed: boolean;
};
export type QrView = {
  id: string; kind: "dynamic" | "static"; code: string | null; linkId: string | null; contentType: string | null; fields: Record<string, string> | null;
  foreground: string; background: string; errorCorrection: string; margin: number; title: string | null; encoded: string; createdAt: string; updatedAt: string;
  scans30d: number;
};
export type BlockView = { id: string; type: string; config: Record<string, string> };
export type BioView = {
  id: string; slug: string; publicUrl: string; title: string; description: string | null; avatarUrl: string | null; theme: string; seoTitle: string | null;
  seoDescription: string | null; seoImageUrl: string | null; noindex: boolean; primaryColor: string | null; secondaryColor: string | null; accentColor: string | null; blocks: BlockView[]; createdAt: string; updatedAt: string; views30d: number;
};
export type BillingPlan = { code: string; name: string; priceXaf: number; priceQuarterlyXaf: number; priceAnnualXaf: number; public: boolean };
export type BillingSubscription = { plan: string; status: "trialing" | "active" | "grace" | "expired"; cycle: string | null; periodStart: string; periodEnd: string; graceEnd: string; paid: boolean };
export type BillingPayment = {
  id: string; plan: string; cycle: string; method: string; status: "pending" | "succeeded" | "failed" | "refunded"; amountXaf: number; listPriceXaf: number; discountXaf: number;
  creditXaf: number; coupon: string | null; failureReason: string | null; invoice: string | null; createdAt: string; paidAt: string | null;
};
export type BillingInvoice = { number: string; plan: string; cycle: string; grossXaf: number; netXaf: number; vatXaf: number; vatRateBp: number; issuedAt: string; periodStart: string; periodEnd: string };
export type BillingView = {
  plan: BillingPlan; subscription: BillingSubscription | null; plans: BillingPlan[]; payments: BillingPayment[]; invoices: BillingInvoice[]; trialAvailable: boolean;
  vatRatesBp: Record<string, number>;
};
export type BillingQuote = { plan: string; cycle: string; listPriceXaf: number; discountXaf: number; creditXaf: number; amountXaf: number; coupon: string | null; newPeriodEnd: string };
export type DomainDns = { txtName: string | null; txtValue: string | null; cnameTarget: string };
export type DomainRow = {
  id: string; hostname: string; dnsStatus: "pending" | "verified" | "failed"; tlsStatus: "pending" | "active"; isDefault: boolean; blocked: boolean; blockedReason: string | null;
  lastError: string | null; rootRedirectUrl: string | null; notFoundUrl: string | null; links: number; verifiedAt: string | null; lastCheckAt: string | null; createdAt: string; dns: DomainDns;
};
export type DomainsView = { items: DomainRow[]; targets: DomainDns };
export type Me = { id: string; email: string; emailVerified: boolean; totpEnabled: boolean; hasPassword: boolean; locale: string };


export type RoutingRule = {countries: string[]; os: string[]; devices: string[]; languages: string[]; url: string};
export type LinkRules = {expiresAt: string | null; deeplink: boolean; onelink: {ios: string | null; android: string | null; desktop: string | null} | null; routes: RoutingRule[]; variants: {url: string; weight: number}[]; utm: {source: string | null; medium: string | null; campaign: string | null; term: string | null; content: string | null} | null};
export type Simulation = {profile: string; appFound: boolean; appName: string | null; mode: string; webUrl: string; iosUrl: string | null; androidUrl: string | null; os: string; inApp: boolean; note: string};
