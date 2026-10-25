"use client";
import { useState, type FormEvent } from "react";
import { post, type Simulation } from "@/lib/api";
import type { Locale } from "@/lib/i18n";
export function Simulator({
  locale,
  profiles,
}: {
  locale: Locale;
  profiles: string[];
}) {
  const en = locale === "en",
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [result, setResult] = useState<Simulation>();
  async function simulate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setResult(undefined);
    const d = new FormData(e.currentTarget);
    const r = await post<Simulation>("/deeplinks/simulate", {
      url: d.get("url"),
      profile: d.get("profile"),
    });
    setBusy(false);
    if (r.ok) setResult(r.data);
    else
      setError(
        en
          ? "Simulation unavailable. Check the URL and try again."
          : "Simulation indisponible. Vérifiez l’URL et réessayez.",
      );
  }
  return (
    <div className="bp-stack">
      <form className="bp-stack" onSubmit={simulate}>
        <label className="bp-field">
          URL
          <input type="url" name="url" required placeholder="https://…" />
        </label>
        <label className="bp-field">
          {en ? "Visitor profile" : "Profil visiteur"}
          <select name="profile">
            {profiles.map((p) => (
              <option key={p}>{p}</option>
            ))}
          </select>
        </label>
        <button className="bp-btn" disabled={busy || profiles.length === 0}>
          {busy
            ? en
              ? "Simulating…"
              : "Simulation…"
            : en
              ? "Simulate"
              : "Simuler"}
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      {result && (
        <section className="bp-card bp-stack" aria-live="polite">
          <h2>
            {result.appName ?? (en ? "Web destination" : "Destination web")}
          </h2>
          <p>{result.note}</p>
          <dl>
            <dt>{en ? "Behaviour" : "Comportement"}</dt>
            <dd>{result.mode}</dd>
            <dt>OS</dt>
            <dd>{result.os}</dd>
            <dt>{en ? "In-app browser" : "Navigateur intégré"}</dt>
            <dd>{result.inApp ? (en ? "Yes" : "Oui") : en ? "No" : "Non"}</dd>
            <dt>Web</dt>
            <dd className="break">{result.webUrl}</dd>
            {result.iosUrl && (
              <>
                <dt>iOS</dt>
                <dd className="break">{result.iosUrl}</dd>
              </>
            )}
            {result.androidUrl && (
              <>
                <dt>Android</dt>
                <dd className="break">{result.androidUrl}</dd>
              </>
            )}
          </dl>
          <p className="muted small">
            {en
              ? "This preview does not verify app installation. Confirm behaviour on a real device."
              : "Cet aperçu ne vérifie pas l’installation de l’application. Confirmez le comportement sur un appareil réel."}
          </p>
        </section>
      )}
    </div>
  );
}
