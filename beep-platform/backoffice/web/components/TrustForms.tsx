"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { send, errorMessage } from "@/lib/client";
import type { FeatureFlag } from "@/lib/api";

export function TrustAction({
  path,
  label,
  method = "POST",
  values = {},
  confirm = false,
}: {
  path: string;
  label: string;
  method?: "POST" | "PUT";
  values?: Record<string, unknown>;
  confirm?: boolean;
}) {
  const router = useRouter(),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setMessage("");
    const d = new FormData(e.currentTarget);
    const r = await send(method, path, {
      ...values,
      reason: d.get("reason"),
      totp: d.get("totp"),
    });
    setBusy(false);
    if (r.ok) {
      setMessage("Action enregistrée.");
      router.refresh();
    } else setMessage(errorMessage(r.code));
  }
  return (
    <form className="bp-stack" onSubmit={submit}>
      <label className="bp-field">
        Motif
        <input name="reason" required minLength={3} maxLength={1000} />
      </label>
      <label className="bp-field">
        Code de sécurité
        <input
          name="totp"
          required
          inputMode="numeric"
          pattern="[0-9]{6}"
          autoComplete="one-time-code"
        />
      </label>
      {confirm && (
        <label className="bp-row">
          <input type="checkbox" required />
          Je confirme cette action.
        </label>
      )}
      <button className="bp-btn bp-btn--secondary" disabled={busy}>
        {busy ? "Enregistrement…" : label}
      </button>
      {message && <p role="status">{message}</p>}
    </form>
  );
}
export function BlockHostForm() {
  const router = useRouter(),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    setBusy(true);
    const r = await send(
      "PUT",
      `/moderation/blocklist/${encodeURIComponent(String(d.get("host")).trim())}`,
      { blocked: true, reason: d.get("reason"), totp: d.get("totp") },
    );
    setBusy(false);
    setMessage(r.ok ? "Hôte bloqué." : errorMessage(r.code));
    if (r.ok) router.refresh();
  }
  return (
    <form className="bp-stack" onSubmit={submit}>
      <label className="bp-field">
        Hôte à bloquer
        <input
          name="host"
          required
          placeholder="example.com"
          autoCapitalize="none"
        />
      </label>
      <label className="bp-field">
        Motif
        <input name="reason" required minLength={3} maxLength={1000} />
      </label>
      <label className="bp-field">
        Code de sécurité
        <input
          name="totp"
          required
          inputMode="numeric"
          pattern="[0-9]{6}"
          autoComplete="one-time-code"
        />
      </label>
      <button className="bp-btn" disabled={busy}>
        {busy ? "Blocage…" : "Bloquer l’hôte et ses sous-domaines"}
      </button>
      {message && <p role="status">{message}</p>}
    </form>
  );
}
export function FlagForm({
  flag,
  canWrite,
}: {
  flag: FeatureFlag;
  canWrite: boolean;
}) {
  const router = useRouter(),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    setBusy(true);
    const r = await send(
      "PUT",
      `/feature-flags/${encodeURIComponent(flag.key)}`,
      {
        enabled: d.has("enabled"),
        rolloutPercent: Number(d.get("rollout")),
        version: flag.version,
        reason: d.get("reason"),
        totp: d.get("totp"),
      },
    );
    setBusy(false);
    setMessage(
      r.ok
        ? "Version publiée."
        : r.code === "conflict"
          ? "Une version plus récente existe. Rechargez cette page."
          : errorMessage(r.code),
    );
    if (r.ok) router.refresh();
  }
  return (
    <form className="bp-stack" onSubmit={submit}>
      <fieldset className="bp-stack" disabled={!canWrite || busy}>
        <legend>
          {flag.key} · v{flag.version}
        </legend>
        <label className="bp-row">
          <input name="enabled" type="checkbox" defaultChecked={flag.enabled} />
          Activé
        </label>
        <label className="bp-field">
          Déploiement (%)
          <input
            name="rollout"
            type="number"
            min={0}
            max={100}
            required
            defaultValue={flag.rolloutPercent}
          />
        </label>
        <p className="muted small">
          Répartition stable par compte ; les droits du plan restent appliqués.
        </p>
        <label className="bp-field">
          Motif
          <input name="reason" required minLength={3} maxLength={1000} />
        </label>
        <label className="bp-field">
          Code de sécurité
          <input
            name="totp"
            required
            inputMode="numeric"
            pattern="[0-9]{6}"
            autoComplete="one-time-code"
          />
        </label>
        <button className="bp-btn" disabled={!canWrite || busy}>
          {busy ? "Publication…" : "Publier"}
        </button>
      </fieldset>
      {message && <p role="status">{message}</p>}
    </form>
  );
}
export function StaffPrivacy({
  userId,
  canExport,
  canErase,
}: {
  userId: string;
  canExport: boolean;
  canErase: boolean;
}) {
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    const action = (e.nativeEvent as SubmitEvent).submitter?.getAttribute(
      "value",
    );
    setBusy(true);
    const r = await send(
      "POST",
      `/users/${userId}/${action === "erase" ? "erase" : "export"}`,
      { reason: d.get("reason"), totp: d.get("totp") },
    );
    setBusy(false);
    if (!r.ok) {
      setMessage(errorMessage(r.code));
      return;
    }
    if (action === "erase") {
      window.location.assign("/users");
      return;
    }
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(r.data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "beep-account.json";
    a.click();
    URL.revokeObjectURL(url);
    setMessage("Export téléchargé et action auditée.");
  }
  return (
    <section className="bp-card bp-stack">
      <h2>Données personnelles</h2>
      <p>
        Export portable ou suppression définitive du compte et de ses
        ressources. Les pièces comptables sont archivées séparément.
      </p>
      <form className="bp-stack" onSubmit={submit}>
        <label className="bp-field">
          Motif / référence de la demande
          <input name="reason" minLength={3} maxLength={1000} required />
        </label>
        <label className="bp-field">
          Code de sécurité
          <input
            name="totp"
            required
            inputMode="numeric"
            pattern="[0-9]{6}"
            autoComplete="one-time-code"
          />
        </label>
        {canExport && (
          <button
            className="bp-btn bp-btn--secondary"
            value="export"
            disabled={busy}
          >
            Exporter les données
          </button>
        )}
        {canErase && (
          <>
            <label className="bp-row">
              <input type="checkbox" name="confirmed" id="erase-confirm" />
              Je confirme avoir vérifié la demande de suppression définitive.
            </label>
            <button
              className="bp-btn bp-btn--danger"
              value="erase"
              disabled={busy}
              onClick={(e) => {
                const form = e.currentTarget.form;
                if (
                  !(form?.elements.namedItem("confirmed") as HTMLInputElement)
                    ?.checked
                ) {
                  e.preventDefault();
                  setMessage(
                    "Confirmez la demande de suppression avant de continuer.",
                  );
                }
              }}
            >
              Supprimer définitivement
            </button>
          </>
        )}
        {message && <p role="status">{message}</p>}
      </form>
    </section>
  );
}
