// Appels vers beep-core. Navigateur : chemin relatif /v1 (même hôte, routé par Caddy ; cookie de session host-only).
// Serveur (SSR) : CORE_INTERNAL_URL (réseau Docker).

export type ApiResult<T = Record<string, unknown>> = { ok: boolean; status: number; code?: string; data?: T };

export async function post<T = Record<string, unknown>>(path: string, body?: unknown): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`/v1${path}`, {
      method: "POST",
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
