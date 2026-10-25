"use client";

import { useState, type FormEvent } from "react";
import { Alert, Button, TextField } from "@beep/ui";
import { errorMessage, send } from "@/lib/client";
import type { AppRow, AppTest } from "@/lib/api";

const PROFILES: [string, string][] = [
  ["iphone-safari", "iPhone · Safari"],
  ["iphone-instagram", "iPhone · Instagram"],
  ["iphone-facebook", "iPhone · Facebook"],
  ["android-chrome", "Android · Chrome"],
  ["android-instagram", "Android · Instagram"],
  ["android-tiktok", "Android · TikTok"],
  ["desktop-chrome", "Ordinateur"],
];
const val = (e: FormEvent<HTMLFormElement>, n: string) => String(new FormData(e.currentTarget).get(n) ?? "");
const lines = (s: string) => s.split(/[\n,]/).map((x) => x.trim()).filter(Boolean);

/** Édition d'une entrée : test en direct (avant publication), puis publication versionnée (TOTP). */
export function CatalogEditor({ app, canWrite }: { app: AppRow | null; canWrite: boolean }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [results, setResults] = useState<AppTest[]>();
  function candidate(e: FormEvent<HTMLFormElement>) {
    return {
      code: app?.code ?? val(e, "code"),
      name: val(e, "name"),
      hosts: lines(val(e, "hosts")),
      iosTemplate: val(e, "ios") || null,
      androidPackage: val(e, "pkg") || null,
      androidTemplate: val(e, "android") || null,
    };
  }
  async function test(form: HTMLFormElement) {
    setBusy(true);
    setError(undefined);
    const e = { currentTarget: form } as FormEvent<HTMLFormElement>;
    const url = val(e, "url");
    const out: AppTest[] = [];
    for (const [profile] of PROFILES) {
      const r = await send<AppTest>("POST", "/catalog/test", { url, profile, candidate: { ...candidate(e), enabled: true, version: 0 } });
      if (!r.ok || !r.data) {
        setBusy(false);
        return setError(errorMessage(r.code));
      }
      out.push(r.data);
    }
    setBusy(false);
    setResults(out);
  }
  return (
    <form
      className="bp-stack"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(undefined);
        const c = candidate(e);
        const r = await send("PUT", `/catalog/${c.code}`, { ...c, enabled: new FormData(e.currentTarget).get("enabled") === "on", totp: val(e, "totp") });
        setBusy(false);
        if (!r.ok) return setError(errorMessage(r.code));
        window.location.href = "/catalog";
      }}
    >
      {error && <Alert tone="danger">{error}</Alert>}
      {!app && <TextField name="code" label="Code" hint="Minuscules, chiffres, - et _." pattern="[a-z][a-z0-9_-]{1,31}" required />}
      <TextField name="name" label="Nom" defaultValue={app?.name} required maxLength={60} />
      <label className="bp-field">
        <span className="bp-field__label">Domaines revendiqués (un par ligne ; les sous-domaines sont inclus)</span>
        <textarea className="bp-input" name="hosts" rows={3} defaultValue={app?.hosts.join("\n")} required />
      </label>
      <TextField name="ios" label="Modèle de schéma iOS" hint="Ex. youtube://{host}{path}{query}. Vide : liens universels (iOS ouvre l'app depuis l'adresse web)." defaultValue={app?.iosTemplate ?? ""} />
      <TextField name="pkg" label="Paquet Android" hint="Ex. com.google.android.youtube : génère l'intent standard avec repli web." defaultValue={app?.androidPackage ?? ""} />
      <TextField name="android" label="Modèle Android personnalisé (facultatif)" defaultValue={app?.androidTemplate ?? ""} />
      <label className="check">
        <input type="checkbox" name="enabled" defaultChecked={app?.enabled ?? true} /> Actif
      </label>
      <TextField name="url" label="Adresse de test" hint="Une adresse réelle de cette application, testée sur 7 profils d'appareils avant publication." />
      <Button type="button" variant="secondary" loading={busy} onClick={(ev) => test(ev.currentTarget.form!)}>
        Tester sur les 7 profils
      </Button>
      {results && (
        <ul className="history">
          {results.map((r, i) => (
            <li key={r.profile}>
              <strong>{PROFILES[i][1]}</strong> — {r.mode}
              <div className="muted small break">{r.note}</div>
              {r.iosUrl && r.os === "ios" && <code className="break small">{r.iosUrl}</code>}
              {r.androidUrl && r.os === "android" && <code className="break small">{r.androidUrl}</code>}
            </li>
          ))}
        </ul>
      )}
      {canWrite && (
        <>
          <TextField name="totp" label="Code de vérification" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required />
          <Button type="submit" loading={busy}>
            Publier une nouvelle version
          </Button>
        </>
      )}
    </form>
  );
}
