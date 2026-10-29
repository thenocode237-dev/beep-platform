import "server-only";
import { cookies } from "next/headers";

// Rendu serveur uniquement (le paquet `server-only` fait échouer le build si un composant client l'importe).
// BO_API_INTERNAL_URL (réseau Docker). Le cookie de session staff est transmis tel quel.
const base = () => process.env.BO_API_INTERNAL_URL ?? "http://backoffice-api:8081/api";

export type Staff = { id: string; email: string; role: string; permissions: string[] };
export type UserRow = { id: string; email: string; status: string; emailVerified: boolean; totpEnabled: boolean; createdAt: string };
export type UsersPage = { items: UserRow[]; nextCursor: string | null };
export type CoreStatus = { core: "up" | "down" };
export type Workspace = { id: string; name: string; plan: string; status: string; createdAt: string; firstLinkAt: string | null };
export type Usage = { resource: string; used: number; limit: number; period: string; level: "ok" | "warning" | "reached" };
export type Override = { key: string; value: number; reason: string; updatedAt: string; updatedBy: string | null };
export type UserDetail = { user: UserRow; workspace: Workspace | null; usage: Usage[]; entitlements: Record<string, number>; overrides: Override[] };
export type LinkRow = {
  id: string; slug: string; destination: string; title: string | null; status: "active" | "disabled"; disabledReason: string | null;
  workspaceId: string; ownerEmail: string | null; createdAt: string; updatedAt: string;
};
export type LinksPage = { items: LinkRow[]; nextCursor: string | null };
export type Plan = {
  code: string; name: string; version: number; priceXaf: number; priceQuarterlyXaf: number; priceAnnualXaf: number; isPublic: boolean; entitlements: Record<string, number>;
};
export type AppRow = { code: string; name: string; hosts: string[]; iosTemplate: string | null; androidPackage: string | null; androidTemplate: string | null; enabled: boolean; version: number };
export type AppTest = { profile: string; appFound: boolean; appName: string | null; mode: "web" | "android_intent" | "interstitial"; webUrl: string; iosUrl: string | null; androidUrl: string | null; os: string; inApp: boolean; note: string };
export type DomainRow = {
  id: string; workspaceId: string; ownerEmail: string | null; hostname: string; dnsStatus: "pending" | "verified" | "failed"; tlsStatus: "pending" | "active"; isDefault: boolean;
  blocked: boolean; blockedReason: string | null; lastError: string | null; links: number; verifiedAt: string | null; lastCheckAt: string | null; createdAt: string;
};
export type DomainsPage = { items: DomainRow[]; nextCursor: string | null };
export type BillingSummary = {
  trialing: number; active: number; grace: number; expired: number; mrrXaf: number; arrXaf: number; revenue30dXaf: number; pendingPayments: number; failedPayments: number;
};
export type Subscription = {
  workspaceId: string; workspaceName: string | null; ownerEmail: string | null; plan: string; status: "trialing" | "active" | "grace" | "expired"; cycle: string | null;
  periodStart: string; periodEnd: string; graceEnd: string; monthlyRateXaf: number;
};
export type Payment = {
  id: string; workspaceId: string; workspaceName: string | null; ownerEmail: string | null; plan: string; cycle: string; method: string; provider: string;
  status: "pending" | "succeeded" | "failed" | "refunded"; amountXaf: number; listPriceXaf: number; discountXaf: number; creditXaf: number; coupon: string | null;
  country: string; note: string | null; recordedBy: string | null; failureReason: string | null; createdAt: string; paidAt: string | null;
};
export type PaymentsPage = { items: Payment[]; nextCursor: string | null };
export type InvoiceRow = { number: string; paymentId: string; plan: string; cycle: string; grossXaf: number; netXaf: number; vatXaf: number; vatRateBp: number; country: string; issuedAt: string };
export type Coupon = {
  code: string; percentOff: number; amountOffXaf: number; maxRedemptions: number | null; redeemed: number; validUntil: string | null; plans: string[]; active: boolean; createdAt: string;
};
export type PlanDraft = {
  code: string; name: string; priceXaf: number; priceQuarterlyXaf: number | null; priceAnnualXaf: number | null; isPublic: boolean; entitlements: Record<string, number>;
  baseVersion: number; note: string | null; updatedBy: string; updatedAt: string; publishedVersion: number | null; publishedAt: string | null;
};
export type DayCount = { day: string; count: number };
export type Metrics = {
  core: {
    generatedAt: string;
    users: { total: number; active: number; pending: number; suspended: number };
    signups: DayCount[];
    activation: { signups: number; activated: number; rate: number };
    clicks: DayCount[];
    resources: { links: number; qrCodes: number; bioPages: number };
    database: { beepBytes: number };
    worker: { lastRollupAt: string | null; lagSeconds: number | null };
    events: { queued: number; dropped: number; written: number };
  };
  backofficeBytes: number;
};
export type Job = {
  name: string; description: string; schedule: string; lastStartedAt: string | null; lastFinishedAt: string | null; lastOk: boolean | null;
  lastDetail: string | null; lastSuccessAt: string | null; runs24h: number; failures24h: number;
};
export type AuditEntry = { seq: number; at: string; actorEmail: string | null; action: string; target: string | null; detail: string | null; ip: string | null; hash: string };
export type AuditPage = { items: AuditEntry[]; nextBefore: number | null; intact: boolean; brokenAt: number; total: number };

export type Result<T> = { status: number; data?: T };

async function get<T>(path: string): Promise<Result<T>> {
  const cookie = (await cookies()).toString();
  try {
    const res = await fetch(`${base()}${path}`, { headers: { cookie }, cache: "no-store" });
    return { status: res.status, data: res.ok ? ((await res.json()) as T) : undefined };
  } catch {
    return { status: 0 };
  }
}

const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

export const getMe = () => get<Staff>("/auth/me");
export const getStatus = () => get<CoreStatus>("/status");
export const getUsers = (cursor?: string, q?: string) => get<UsersPage>(`/users${qs({ limit: 20, cursor, q })}`);
export const getUser = (id: string) => get<UserDetail>(`/users/${encodeURIComponent(id)}`);
export const getLinks = (p: { q?: string; workspaceId?: string; cursor?: string }) => get<LinksPage>(`/links${qs({ limit: 20, ...p })}`);
export const getLink = (id: string) => get<LinkRow>(`/links/${encodeURIComponent(id)}`);
export const getReservedSlugs = () => get<{ items: string[] }>("/reserved-slugs");
export const getPlans = () => get<{ items: Plan[] }>("/plans");
export const getCatalog = () => get<{ items: AppRow[] }>("/catalog");
export const getDomains = (p: { q?: string; status?: string; cursor?: string }) => get<DomainsPage>(`/domains${qs({ limit: 20, ...p })}`);
export const getBillingSummary = () => get<BillingSummary>("/billing/summary");
export const getSubscriptions = (status?: string) => get<{ items: Subscription[] }>(`/billing/subscriptions${qs({ limit: 50, status })}`);
export const getUpcoming = (days = 14) => get<{ items: Subscription[] }>(`/billing/upcoming${qs({ days, limit: 50 })}`);
export const getPayments = (p: { status?: string; workspaceId?: string; cursor?: string }) => get<PaymentsPage>(`/billing/payments${qs({ limit: 20, ...p })}`);
export const getWorkspaceInvoices = (id: string) => get<{ items: InvoiceRow[] }>(`/workspaces/${encodeURIComponent(id)}/invoices`);
export const getCoupons = () => get<{ items: Coupon[] }>("/coupons");
export const getPlanDrafts = () => get<{ items: PlanDraft[] }>("/plan-drafts");
export const getMetrics = (days = 30) => get<Metrics>(`/metrics${qs({ days })}`);
export const getJobs = () => get<{ items: Job[] }>("/jobs");
export const getAudit = (p: { before?: string; action?: string; actor?: string }) => get<AuditPage>(`/audit${qs({ limit: 50, ...p })}`);


export type AbuseReport={id:string;url:string;category:string;detail:string;source:string;status:string;reason:string|null;createdAt:string;resolvedAt:string|null};
export type Reports={items:AbuseReport[];open:number;overdue:number};
export type BlockedHost={host:string;reason:string;createdAt:string};
export type FeatureFlag={key:string;enabled:boolean;rolloutPercent:number;version:number;reason:string};
export const getReports=(status="open",offset=0)=>get<Reports>(`/moderation/reports${qs({status,offset,limit:50})}`);
export const getBlocks=()=>get<{items:BlockedHost[]}>("/moderation/blocklist");
export const getFlags=()=>get<{items:FeatureFlag[]}>("/feature-flags");

export const getEnterprise=()=>get<Record<string,unknown>>("/enterprise/overview");
