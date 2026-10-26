import "server-only";
import { cookies, headers } from "next/headers";

/** Lecture côté serveur (SSR) avec le cookie de session de la requête. CORE_INTERNAL_URL : réseau Docker. */
export async function coreGet<T>(
  path: string,
): Promise<{ status: number; data?: T }> {
  const base = process.env.CORE_INTERNAL_URL ?? "http://core:8080/v1";
  const cookie = (await cookies()).toString();
  if (!cookie) return { status: 401 };
  try {
    const forwarded = (await headers()).get("x-forwarded-for");
    const res = await fetch(`${base}${path}`, {
      headers: {
        cookie,
        ...(forwarded ? { "X-Forwarded-For": forwarded } : {}),
      },
      cache: "no-store",
    });
    return {
      status: res.status,
      data: res.ok ? ((await res.json()) as T) : undefined,
    };
  } catch {
    return { status: 0 };
  }
}

/** Fournisseurs OAuth configurés (sans session). */
export async function oauthProviders(): Promise<string[]> {
  const base = process.env.CORE_INTERNAL_URL ?? "http://core:8080/v1";
  try {
    const res = await fetch(`${base}/auth/providers`, { cache: "no-store" });
    return res.ok
      ? (((await res.json()) as { oauth: string[] }).oauth ?? [])
      : [];
  } catch {
    return [];
  }
}
