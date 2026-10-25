"use client";

import { useState, type FormEvent } from "react";
import { Alert, Button, TextField } from "@beep/ui";
import { errorMessage, send } from "@/lib/client";

const field = (e: FormEvent<HTMLFormElement>, name: string) => String(new FormData(e.currentTarget).get(name) ?? "");

/** Relance de la vérification DNS, blocage (motif + TOTP) ou déblocage (TOTP) d'un domaine. */
export function DomainActions({ id, blocked, canModerate }: { id: string; blocked: boolean; canModerate: boolean }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  async function run(fn: () => ReturnType<typeof send>) {
    setBusy(true);
    setError(undefined);
    const r = await fn();
    setBusy(false);
    if (!r.ok) return setError(errorMessage(r.code));
    window.location.reload();
  }
  if (!canModerate) return null;
  return (
    <div className="bp-stack">
      {error && <Alert tone="danger">{error}</Alert>}
      <Button variant="secondary" size="sm" loading={busy} onClick={() => run(() => send("POST", `/domains/${id}/recheck`, {}))}>
        Relancer la vérification DNS
      </Button>
      <form
        className="bp-stack"
        onSubmit={(e) => {
          e.preventDefault();
          run(() => (blocked ? send("POST", `/domains/${id}/unblock`, { totp: field(e, "totp") }) : send("POST", `/domains/${id}/block`, { reason: field(e, "reason"), totp: field(e, "totp") })));
        }}
      >
        {!blocked && <TextField name="reason" label="Motif du blocage (journalisé)" required minLength={3} maxLength={500} />}
        <TextField name="totp" label="Code de vérification" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required />
        <Button type="submit" variant={blocked ? "accent" : "primary"} size="sm" loading={busy}>
          {blocked ? "Débloquer le domaine" : "Bloquer le domaine"}
        </Button>
      </form>
    </div>
  );
}
