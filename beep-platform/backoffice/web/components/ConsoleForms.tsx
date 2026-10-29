"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { Alert, Button, TextField } from "@beep/ui";
import { errorMessage, send } from "@/lib/client";
import { entitlementLabels } from "@/lib/format";

function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [done, setDone] = useState<string>();
  async function run(fn: () => ReturnType<typeof send>, success: string, reload = true) {
    setBusy(true);
    setError(undefined);
    setDone(undefined);
    const r = await fn();
    setBusy(false);
    if (!r.ok) return setError(errorMessage(r.code));
    setDone(success);
    if (reload) window.setTimeout(() => window.location.reload(), 600);
  }
  return { busy, error, done, run };
}

function Feedback({ error, done }: { error?: string; done?: string }) {
  return (
    <>
      {error && <Alert tone="danger">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
    </>
  );
}

const TotpField = () => (
  <TextField
    name="totp"
    label="Code de vérification"
    hint="Action sensible : saisissez le code actuel de votre application d'authentification."
    inputMode="numeric"
    autoComplete="one-time-code"
    pattern="[0-9]{6}"
    maxLength={6}
    required
  />
);

function field(e: FormEvent<HTMLFormElement>, name: string) {
  return String(new FormData(e.currentTarget).get(name) ?? "");
}

/** Suspension / réactivation d'un compte : motif + ré-authentification TOTP. */
export function AccountStatusForm({ userId, suspended }: { userId: string; suspended: boolean }) {
  const a = useAction();
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const body = { status: suspended ? "active" : "suspended", reason: field(e, "reason"), totp: field(e, "totp") };
        a.run(() => send("POST", `/users/${userId}/status`, body), suspended ? "Compte réactivé." : "Compte suspendu.");
      }}
    >
      <p className="muted">
        {suspended
          ? "Réactiver rétablit la connexion et les redirections du workspace."
          : "Suspendre bloque la connexion, révoque les sessions et remplace les redirections du workspace par une page d'information."}
      </p>
      <Feedback {...a} />
      <TextField name="reason" label="Motif (journalisé)" required minLength={3} maxLength={500} />
      <TotpField />
      <Button type="submit" variant={suspended ? "accent" : "primary"} loading={a.busy}>
        {suspended ? "Réactiver le compte" : "Suspendre le compte"}
      </Button>
    </form>
  );
}

/** Override d'un droit pour un workspace : valeur, ou retour à la valeur du plan. */
export function OverrideForm({ workspaceId, current }: { workspaceId: string; current: Record<string, number> }) {
  const a = useAction();
  const [key, setKey] = useState(Object.keys(entitlementLabels)[0]);
  const [reset, setReset] = useState(false);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const value = reset ? null : Number(field(e, "value"));
        a.run(
          () => send("PUT", `/workspaces/${workspaceId}/entitlements`, { overrides: { [key]: value }, reason: field(e, "reason"), totp: field(e, "totp") }),
          "Droits mis à jour.",
        );
      }}
    >
      <Feedback {...a} />
      <div className="bp-field">
        <label className="bp-field__label" htmlFor="override-key">
          Droit
        </label>
        <select id="override-key" className="bp-input" value={key} onChange={(ev) => setKey(ev.target.value)}>
          {Object.entries(entitlementLabels).map(([k, label]) => (
            <option key={k} value={k}>
              {label} (actuel : {current[k] ?? "—"})
            </option>
          ))}
        </select>
      </div>
      <label className="check">
        <input type="checkbox" checked={reset} onChange={(ev) => setReset(ev.target.checked)} /> Revenir à la valeur du plan
      </label>
      {!reset && (
        <TextField
          name="value"
          label="Nouvelle valeur"
          hint={key.startsWith("features.") ? "1 = activé, 0 = désactivé." : "-1 = illimité."}
          inputMode="numeric"
          pattern="-?[0-9]+"
          required
        />
      )}
      <TextField name="reason" label="Motif (journalisé)" hint="Ex. : +50 liens offerts à titre commercial." required minLength={3} maxLength={500} />
      <TotpField />
      <Button type="submit" loading={a.busy}>
        Enregistrer l'override
      </Button>
    </form>
  );
}

/** Désactivation (motif obligatoire) ou réactivation d'un lien. */
export function LinkModerationForm({ linkId, disabled }: { linkId: string; disabled: boolean }) {
  const a = useAction();
  if (disabled)
    return (
      <div className="bp-stack">
        <Feedback {...a} />
        <Button variant="accent" loading={a.busy} onClick={() => a.run(() => send("POST", `/links/${linkId}/enable`), "Lien réactivé.")}>
          Réactiver le lien
        </Button>
      </div>
    );
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        a.run(() => send("POST", `/links/${linkId}/disable`, { reason: field(e, "reason") }), "Lien désactivé : il affiche désormais une page d'avertissement.");
      }}
    >
      <Feedback {...a} />
      <TextField name="reason" label="Motif (journalisé)" hint="Ex. : hameçonnage signalé, contenu illicite." required minLength={3} maxLength={500} />
      <Button type="submit" loading={a.busy}>
        Désactiver le lien
      </Button>
    </form>
  );
}

/** Formulaire GET de recherche (pas de JavaScript requis). */
export function SearchForm({ action, q, label, children }: { action: string; q?: string; label: string; children?: ReactNode }) {
  return (
    <form className="search" action={action} method="get" role="search">
      <TextField name="q" type="search" label={label} defaultValue={q} />
      {children}
      <Button type="submit" variant="secondary">
        Rechercher
      </Button>
    </form>
  );
}
