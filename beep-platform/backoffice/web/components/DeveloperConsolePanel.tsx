"use client";
import { useState, useEffect, type FormEvent } from "react";
import { send, errorMessage } from "@/lib/client";
type Workspace = {
  keys: {
    id: string;
    prefix: string;
    name: string;
    scopes: string[];
    revokedAt: string | null;
  }[];
  deliveries: {
    id: string;
    event: string;
    status: string;
    attempts: number;
    httpStatus: number | null;
    createdAt: string;
  }[];
};
type Monitor = {
  events: number;
  rejections: { reason: string; count: number }[];
  api: { bucket: string; count: number }[];
  deliveries: { status: string; count: number; lagSeconds: number }[];
};
export function DeveloperConsolePanel() {
  const [monitor, setMonitor] = useState<Monitor>(),
    [ws, setWs] = useState(""),
    [view, setView] = useState<Workspace>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [exported, setExported] = useState("");
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const r = await fetch("/api/developer/monitor", { cache: "no-store" });
        if (!r.ok) throw new Error();
        const data = (await r.json()) as Monitor;
        if (active) setMonitor(data);
      } catch {
        if (active) setError("Les métriques sont indisponibles. Réessayez.");
      }
    }
    void refresh();
    const timer = setInterval(refresh, 15000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);
  async function load(id: string) {
    setBusy(true);
    setError("");
    try {
      const r = await fetch(
        `/api/developer/workspaces/${encodeURIComponent(id)}`,
        { cache: "no-store" },
      );
      if (!r.ok) {
        setView(undefined);
        setError(
          r.status === 403
            ? "Votre rôle ne permet pas de consulter cet espace."
            : "Espace introuvable ou service indisponible.",
        );
        return;
      }
      setView((await r.json()) as Workspace);
    } catch {
      setError("Connexion impossible.");
    } finally {
      setBusy(false);
    }
  }
  async function act(
    event: FormEvent<HTMLFormElement>,
    action: string,
    id?: string,
  ) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    const r = await send("POST", `/developer/workspaces/${ws}/actions`, {
      action,
      id: id ?? f.get("id"),
      reason: f.get("reason"),
      totp: f.get("totp"),
      ...(action === "limits"
        ? { rpm: Number(f.get("rpm")), events: Number(f.get("events")) }
        : {}),
    });
    setBusy(false);
    if (!r.ok) {
      setError(errorMessage(r.code));
      return;
    }
    if (action === "visitor.export")
      setExported(JSON.stringify(r.data, null, 2));
    else {
      setError("Action enregistrée dans le journal d’audit.");
      await load(ws);
    }
  }
  function actionForm(action: string, label: string, id?: string) {
    return (
      <form className="bp-stack" onSubmit={(e) => void act(e, action, id)}>
        {!id && action.startsWith("visitor.") && (
          <label>
            Référence visiteur pseudonymisée (64 caractères)
            <input
              className="bp-input"
              name="id"
              required
              pattern="[0-9a-f]{64}"
              maxLength={64}
            />
          </label>
        )}
        {action === "limits" && (
          <>
            <label>
              Requêtes API / minute
              <input
                className="bp-input"
                type="number"
                min={0}
                max={1000000}
                name="rpm"
                required
                defaultValue={600}
              />
            </label>
            <label>
              Événements / mois
              <input
                className="bp-input"
                type="number"
                min={0}
                max={1000000}
                name="events"
                required
                defaultValue={100000}
              />
            </label>
          </>
        )}
        <label>
          Motif
          <input
            className="bp-input"
            name="reason"
            required
            minLength={3}
            maxLength={1000}
          />
        </label>
        <label>
          Code TOTP
          <input
            className="bp-input"
            name="totp"
            required
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
          />
        </label>
        <button className="bp-btn bp-btn--secondary" disabled={busy}>
          {busy ? "En cours…" : label}
        </button>
      </form>
    );
  }
  return (
    <div className="page bp-stack bp-stack--lg">
      <header className="page-heading">
        <p className="eyebrow">Exploitation / Phase 3</p>
        <h1>Conversions & API.</h1>
        <p>
          Débit d’ingestion, refus, accès API et livraisons. Les métriques se
          rafraîchissent toutes les 15 secondes.
        </p>
      </header>
      <p role="status" aria-live="polite">
        {error}
      </p>
      {monitor && (
        <>
          <section className="bp-card bp-stack">
            <h2>Pipeline d’événements</h2>
            <p>
              <strong>{monitor.events}</strong> événements acceptés sur la
              dernière minute
            </p>
            {monitor.rejections.length === 0 ? (
              <p>Aucun refus enregistré sur 24 heures.</p>
            ) : (
              monitor.rejections.map((r) => (
                <p key={r.reason}>
                  {r.reason} : {r.count}
                </p>
              ))
            )}
          </section>
          <section className="bp-card bp-stack">
            <h2>API · 24 heures</h2>
            {monitor.api.length ? (
              monitor.api.map((r) => (
                <p key={r.bucket}>
                  HTTP {r.bucket.replace("status:", "")} : {r.count}
                </p>
              ))
            ) : (
              <p>Aucun appel par clé enregistré.</p>
            )}
          </section>
          <section className="bp-card bp-stack">
            <h2>Livraisons</h2>
            {monitor.deliveries.map((r) => (
              <p key={r.status}>
                {r.status} : {r.count} · ancienneté maximale {r.lagSeconds} s
              </p>
            ))}
          </section>
        </>
      )}
      <section className="bp-card bp-stack">
        <h2>Consulter un espace</h2>
        <form
          className="bp-stack"
          onSubmit={(e) => {
            e.preventDefault();
            void load(ws);
          }}
        >
          <label>
            Identifiant du workspace
            <input
              className="bp-input"
              required
              value={ws}
              onChange={(e) => {
                setWs(e.target.value);
                setView(undefined);
                setExported("");
              }}
              pattern="[0-9a-fA-F-]{36}"
            />
          </label>
          <button className="bp-btn bp-btn--primary" disabled={busy}>
            Charger
          </button>
        </form>
      </section>
      {view && (
        <>
          <section className="bp-card bp-stack">
            <h2>Clés API</h2>
            {view.keys.length === 0 && <p>Aucune clé.</p>}
            {view.keys.map((k) => (
              <article key={k.id} className="bp-card bp-stack">
                <h3>{k.name}</h3>
                <p>
                  <code>{k.prefix}…</code> · {k.scopes.join(", ")} ·{" "}
                  {k.revokedAt ? "Révoquée" : "Active"}
                </p>
                {!k.revokedAt && (
                  <details>
                    <summary>Révoquer cette clé</summary>
                    {actionForm("revoke", "Révoquer", k.id)}
                  </details>
                )}
              </article>
            ))}
          </section>
          <section className="bp-card bp-stack">
            <h2>Limites de l’espace</h2>
            {actionForm("limits", "Appliquer les limites")}
          </section>
          <section className="bp-card bp-stack">
            <h2>Journal des webhooks</h2>
            {view.deliveries.length === 0 && <p>Aucune livraison.</p>}
            {view.deliveries.map((d) => (
              <article key={d.id} className="bp-card bp-stack">
                <h3>{d.event}</h3>
                <p>
                  {d.status} · {d.attempts} tentatives · HTTP{" "}
                  {d.httpStatus ?? "—"}
                </p>
                <p>{d.id}</p>
                {["failed", "delivered"].includes(d.status) && (
                  <details>
                    <summary>Rejouer cette livraison</summary>
                    {actionForm("replay", "Rejouer", d.id)}
                  </details>
                )}
              </article>
            ))}
          </section>
          <section className="bp-card bp-stack">
            <h2>Données d’un visiteur</h2>
            <p>
              La référence est l’empreinte pseudonymisée fournie par le flux
              d’événements de cet espace. Aucun secret API ni adresse IP n’est
              exporté.
            </p>
            <details>
              <summary>Exporter</summary>
              {actionForm("visitor.export", "Exporter")}
            </details>
            <details>
              <summary>Supprimer</summary>
              {actionForm("visitor.erase", "Supprimer les données")}
            </details>
            {exported && (
              <>
                <a
                  className="bp-btn bp-btn--secondary"
                  download="beep-visitor.json"
                  href={`data:application/json;charset=utf-8,${encodeURIComponent(exported)}`}
                >
                  Télécharger l’export
                </a>
                <button
                  className="bp-btn bp-btn--secondary"
                  onClick={() => setExported("")}
                >
                  Fermer l’export
                </button>
              </>
            )}
          </section>
        </>
      )}
    </div>
  );
}
