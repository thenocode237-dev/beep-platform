"use client";
import { useState, type FormEvent } from "react";
import { post } from "@/lib/api";
export function ReportForm({ en }: { en: boolean }) {
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    const d = new FormData(e.currentTarget);
    const r = await post("/reports", {
      url: d.get("url"),
      category: d.get("category"),
      detail: d.get("detail"),
    });
    setBusy(false);
    setError(!r.ok);
    setMessage(
      r.ok
        ? en
          ? "Report received. The team will review it."
          : "Signalement reçu. L’équipe va l’examiner."
        : r.code === "rate_limited"
          ? en
            ? "Too many reports. Please wait."
            : "Trop de signalements. Patientez."
          : en
            ? "Check the URL and try again."
            : "Vérifiez l’URL et réessayez.",
    );
  }
  return (
    <form className="bp-card bp-card--lg bp-stack" onSubmit={submit}>
      <label className="bp-field">
        {en ? "Reported URL" : "URL signalée"}
        <input name="url" type="url" required maxLength={2048} />
      </label>
      <label className="bp-field">
        {en ? "Reason" : "Motif"}
        <select name="category">
          <option value="phishing">Phishing</option>
          <option value="malware">
            {en ? "Malware" : "Logiciel malveillant"}
          </option>
          <option value="spam">Spam</option>
          <option value="other">{en ? "Other" : "Autre"}</option>
        </select>
      </label>
      <label className="bp-field">
        {en
          ? "Details (do not include personal information)"
          : "Précisions (sans données personnelles)"}
        <textarea name="detail" rows={5} maxLength={2000} />
      </label>
      <button className="bp-btn" disabled={busy}>
        {busy
          ? en
            ? "Sending…"
            : "Envoi…"
          : en
            ? "Submit report"
            : "Envoyer le signalement"}
      </button>
      {message && <p role={error ? "alert" : "status"}>{message}</p>}
    </form>
  );
}
