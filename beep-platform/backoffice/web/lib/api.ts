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
export type Plan = { code: string; name: string; version: number; priceXaf: number; isPublic: boolean; entitlements: Record<string, number> };
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
export type Invitation = { id: string; email: string; status: "pending" | "used" | "expired"; invitedBy: string | null; createdAt: string; expiresAt: string; usedAt: string | null };
export type InvitationsPage = { items: Invitation[]; nextCursor: string | null };

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
export const getMetrics = (days = 30) => get<Metrics>(`/metrics${qs({ days })}`);
export const getJobs = () => get<{ items: Job[] }>("/jobs");
export const getAudit = (p: { before?: string; action?: string; actor?: string }) => get<AuditPage>(`/audit${qs({ limit: 50, ...p })}`);
export const getInvitations = (cursor?: string) => get<InvitationsPage>(`/invitations${qs({ limit: 20, cursor })}`);
