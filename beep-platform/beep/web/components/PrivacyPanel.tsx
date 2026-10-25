"use client";
import { useEffect, useState, type FormEvent } from "react";
import { post } from "@/lib/api";
import { href, type Locale } from "@/lib/i18n";
export function PrivacyPanel({
  locale,
  totpEnabled,
}: {
  locale: Locale;
  totpEnabled: boolean;
}) {
  const en = locale === "en",
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState(false),
    [token, setToken] = useState("");
  useEffect(() => {
    const url = new URL(window.location.href);
    const value =
      new URLSearchParams(url.hash.slice(1)).get("erase") ??
      url.searchParams.get("token");
    if (value) {
      setToken(value);
      url.searchParams.delete("token");
      url.hash = "";
      window.history.replaceState(null, "", url.pathname + url.search);
    }
  }, []);
  const text = (fr: string, eng: string) => (en ? eng : fr);
  async function exportData() {
    setBusy(true);
    const r = await post("/account/export");
    setBusy(false);
    setError(!r.ok);
    if (!r.ok) {
      setMessage(
        text(
          "Export indisponible. Réessayez.",
          "Export unavailable. Try again.",
        ),
      );
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
    setMessage(text("Export téléchargé.", "Export downloaded."));
  }
  async function request() {
    setBusy(true);
    const r = await post("/account/erase-request");
    setBusy(false);
    setError(!r.ok);
    setMessage(
      r.ok
        ? text(
            "Un lien de confirmation valable 15 minutes a été envoyé à votre adresse e-mail.",
            "A confirmation link valid for 15 minutes was sent to your email.",
          )
        : text(
            "Demande impossible. Patientez puis réessayez.",
            "Request unavailable. Wait and try again.",
          ),
    );
  }
  async function erase(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    setBusy(true);
    const r = await post("/account/erase", { token, totp: d.get("totp") });
    setBusy(false);
    setError(!r.ok);
    if (r.ok) window.location.assign(href(locale, "/login"));
    else
      setMessage(
        text(
          "Vérifiez le lien de confirmation et votre code de sécurité.",
          "Check the confirmation link and your security code.",
        ),
      );
  }
  return (
    <section
      className="bp-card bp-card--lg bp-stack"
      aria-labelledby="privacy-title"
    >
      <h2 id="privacy-title">
        {text("Vos données et votre compte", "Your data and account")}
      </h2>
      <p>
        {text(
          "Téléchargez vos données avant de fermer votre compte. La suppression retire vos liens, QR, pages, domaines, statistiques et accès. Les pièces comptables sont conservées séparément pour les obligations applicables.",
          "Download your data before closing your account. Deletion removes links, QR codes, pages, domains, analytics and access. Accounting records are retained separately for applicable obligations.",
        )}
      </p>
      <button
        className="bp-btn bp-btn--secondary"
        type="button"
        disabled={busy}
        onClick={exportData}
      >
        {text("Télécharger mes données", "Download my data")}
      </button>
      <button
        className="bp-btn bp-btn--ghost"
        type="button"
        disabled={busy}
        onClick={request}
      >
        {text("Demander la suppression du compte", "Request account deletion")}
      </button>
      {token && (
        <form className="bp-stack" onSubmit={erase}>
          <h3>
            {text(
              "Confirmer la suppression définitive",
              "Confirm permanent deletion",
            )}
          </h3>
          {totpEnabled && (
            <label className="bp-field">
              {text(
                "Code de votre application de sécurité",
                "Authenticator code",
              )}
              <input
                name="totp"
                required
                inputMode="numeric"
                pattern="[0-9]{6}"
                autoComplete="one-time-code"
              />
            </label>
          )}
          <label className="bp-row">
            <input type="checkbox" required />
            {text(
              "Je comprends que mes liens partagés cesseront de fonctionner et que cette action est définitive.",
              "I understand my shared links will stop working and this action is permanent.",
            )}
          </label>
          <button className="bp-btn bp-btn--danger" disabled={busy}>
            {text(
              "Supprimer définitivement mon compte",
              "Permanently delete my account",
            )}
          </button>
        </form>
      )}
      {message && <p role={error ? "alert" : "status"}>{message}</p>}
    </section>
  );
}
