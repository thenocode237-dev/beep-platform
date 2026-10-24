// Appels navigateur (composants client). Chemin relatif /api : même hôte, routé par Caddy ; cookie de session host-only.
export type ClientResult<T = unknown> = { ok: boolean; status: number; code?: string; data?: T };

export async function send<T = unknown>(method: "POST" | "PUT" | "PATCH" | "DELETE", path: string, body?: unknown): Promise<ClientResult<T>> {
  try {
    const res = await fetch(`/api${path}`, {
      method,
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const json = res.headers.get("content-type")?.includes("json") ? await res.json().catch(() => undefined) : undefined;
    return { ok: res.ok, status: res.status, code: json?.code, data: json as T };
  } catch {
    return { ok: false, status: 0, code: "network" };
  }
}

export const post = (path: string, body?: unknown) => send("POST", path, body);

/** Messages des codes d'erreur stables renvoyés par l'API. */
export const errorMessage = (code?: string) =>
  ({
    network: "Connexion impossible. Vérifiez le réseau et réessayez.",
    forbidden: "Votre rôle ne permet pas cette action.",
    reauth_required: "Code de vérification incorrect : saisissez le code actuel de votre application.",
    rate_limited: "Trop de tentatives. Patientez quelques minutes.",
    invalid_reason: "Indiquez un motif (3 caractères minimum).",
    invalid_email: "Adresse e-mail invalide.",
    already_registered: "Cette adresse a déjà un compte confirmé.",
    status_unchanged: "Le compte est déjà dans cet état.",
    unknown_entitlement: "Clé de droit inconnue.",
    invalid_entitlement: "Valeur incohérente pour ce droit (-1 = illimité ; 0 ou 1 pour une fonctionnalité).",
    not_found: "Élément introuvable.",
    core_unavailable: "Le service principal est injoignable. Réessayez dans un instant.",
    unauthorized: "Session expirée : reconnectez-vous.",
  })[code ?? ""] ?? "L'action n'a pas abouti. Réessayez dans un instant.";
