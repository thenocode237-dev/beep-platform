import "server-only";
import { cookies } from "next/headers";

// Rendu serveur uniquement (le paquet `server-only` fait échouer le build si un composant client l'importe).
// BO_API_INTERNAL_URL (réseau Docker). Le cookie de session staff est transmis tel quel.
const base = () => process.env.BO_API_INTERNAL_URL ?? "http://backoffice-api:8081/api";

export type Staff = { id: string; email: string; role: string };
export type UserRow = { id: string; email: string; status: string; emailVerified: boolean; totpEnabled: boolean; createdAt: string };
export type UsersPage = { items: UserRow[]; nextCursor: string | null };
export type CoreStatus = { core: "up" | "down" };

async function get<T>(path: string): Promise<{ status: number; data?: T }> {
  const cookie = (await cookies()).toString();
  try {
    const res = await fetch(`${base()}${path}`, { headers: { cookie }, cache: "no-store" });
    return { status: res.status, data: res.ok ? ((await res.json()) as T) : undefined };
  } catch {
    return { status: 0 };
  }
}

export const getMe = () => get<Staff>("/auth/me");
export const getStatus = () => get<CoreStatus>("/status");
export const getUsers = (cursor?: string) => get<UsersPage>(`/users?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`);
