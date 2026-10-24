// Appels navigateur (composants client). Chemin relatif /api : même hôte, routé par Caddy ; cookie de session host-only.
export async function post(path: string, body?: unknown): Promise<{ ok: boolean; code?: string }> {
  try {
    const res = await fetch(`/api${path}`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const json = res.headers.get("content-type")?.includes("json") ? await res.json().catch(() => undefined) : undefined;
    return { ok: res.ok, code: json?.code };
  } catch {
    return { ok: false, code: "network" };
  }
}
