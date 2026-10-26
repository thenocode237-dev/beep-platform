"use client";
import { useState, type FormEvent, type InputHTMLAttributes } from "react";
type Row = Record<string, unknown>;
function Field({
  label,
  ...p
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="bp-field">
      <span>{label}</span>
      <input {...p} />
    </label>
  );
}
export function EnterprisePanel({
  initial,
  status,
  en,
}: {
  initial: Row;
  status: number;
  en: boolean;
}) {
  const [view, setView] = useState(initial),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [handoff, setHandoff] = useState<Row>({});
  const t = (fr: string, eng: string) => (en ? eng : fr);
  const c = (view.contract ?? {}) as Row;
  const apps = (Array.isArray(view.apps) ? view.apps : []) as Row[];
  const run = async (method: string, path: string, body?: unknown) => {
    setBusy(true);
    setMessage("");
    try {
      const r = await fetch("/v1" + path, {
        method,
        headers: { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        cache: "no-store",
      });
      const data = (await r.json()) as Row;
      if (!r.ok) {
        setMessage(
          data.code === "feature_unavailable"
            ? t(
                "Un contrat entreprise actif est nécessaire.",
                "An active enterprise contract is required.",
              )
            : t(
                "Action impossible. Vérifiez vos droits et les champs saisis.",
                "Unable to complete this action. Check permissions and fields.",
              ),
        );
        return;
      }
      if (path.startsWith("/mobile/")) setHandoff(data);
      else {
        const response = await fetch("/v1/enterprise", { cache: "no-store" });
        if (response.ok) setView((await response.json()) as Row);
        setMessage(t("Enregistré.", "Saved."));
      }
    } catch {
      setMessage(
        t("Connexion impossible. Réessayez.", "Connection failed. Try again."),
      );
    } finally {
      setBusy(false);
    }
  };
  const form =
    (action: (d: FormData) => Promise<void>) =>
    async (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      await action(new FormData(e.currentTarget));
    };
  return (
    <div className="bp-stack">
      <header>
        <p className="eyebrow">
          {t("Votre organisation", "Your organization")}
        </p>
        <h1>{t("Offre entreprise", "Enterprise")}</h1>
        <p>
          {t(
            "Votre contrat et vos applications mobiles.",
            "Your contract and mobile applications.",
          )}
        </p>
      </header>
      <p role="status" aria-live="polite">
        {message}
      </p>
      {status !== 200 && (
        <p role="alert">
          {t(
            "Consultation indisponible. Vérifiez la session et la politique d’accès du workspace.",
            "View unavailable. Check your session and workspace access policy.",
          )}
        </p>
      )}
      <section className="bp-card bp-stack">
        <h2>{t("Votre contrat", "Your contract")}</h2>
        {c.reference ? (
          <>
            <strong>{String(c.reference)}</strong>
            <dl>
              <dt>{t("État", "Status")}</dt>
              <dd>{String(c.status)}</dd>
              <dt>SLA</dt>
              <dd>{Number(c.slaBp) / 100} %</dd>
              <dt>{t("Rétention des statistiques", "Analytics retention")}</dt>
              <dd>
                {String(c.retentionDays)} {t("jours", "days")}
              </dd>
              <dt>{t("Échéance", "End date")}</dt>
              <dd>
                {new Date(String(c.endsAt)).toLocaleDateString(
                  en ? "en-GB" : "fr-FR",
                )}
              </dd>
              <dt>DPA</dt>
              <dd>{String(c.dpaReference)}</dd>
            </dl>
            <a
              className="bp-btn bp-btn--secondary"
              href={"/v1/auth/sso/" + String(view.workspaceId) + "/start"}
            >
              {t("Se connecter avec SSO", "Sign in with SSO")}
            </a>
          </>
        ) : (
          <p>
            {t(
              "Aucun contrat sur mesure. Contactez l’équipe via le support pour définir votre offre.",
              "No custom contract. Contact our team through support to define your offer.",
            )}
          </p>
        )}
      </section>
      <section className="bp-card bp-stack">
        <h2>{t("Applications mobiles", "Mobile applications")}</h2>
        <p>
          {t(
            "Conservez la destination d’un lien pendant l’installation, avec le consentement de la personne. Le jeton expire après 24 heures et ne peut être utilisé qu’une fois.",
            "Preserve a link destination through installation with the person’s consent. Tokens expire after 24 hours and can only be used once.",
          )}
        </p>
        {apps.length === 0 && (
          <p>
            {t(
              "Aucune application enregistrée.",
              "No registered applications.",
            )}
          </p>
        )}
        {apps.map((a) => (
          <article className="bp-card bp-stack" key={String(a.id)}>
            <strong>{String(a.name)}</strong>
            <code className="break">{String(a.id)}</code>
            <span>
              {String(a.scheme)}:// ·{" "}
              {a.enabled ? t("Active", "Active") : t("Désactivée", "Disabled")}
            </span>
            {view.manages === true && a.enabled === true && (
              <button
                className="bp-btn bp-btn--secondary"
                disabled={busy}
                onClick={() =>
                  void run("DELETE", "/enterprise/mobile-apps/" + String(a.id))
                }
              >
                {t("Désactiver", "Disable")}
              </button>
            )}
          </article>
        ))}
        {view.manages === true && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/mobile-apps", Object.fromEntries(d)),
            )}
          >
            <Field
              name="name"
              label={t("Nom de l’application", "Application name")}
              required
              maxLength={80}
            />
            <Field
              name="scheme"
              label={t(
                "Schéma de l’application (ex. monapp)",
                "Application scheme (e.g. myapp)",
              )}
              required
              pattern="[a-z][a-z0-9+.-]{1,39}"
            />
            <Field
              name="androidStore"
              type="url"
              label="Google Play URL"
              required
            />
            <Field name="iosStore" type="url" label="App Store URL" required />
            <button className="bp-btn bp-btn--primary" disabled={busy}>
              {t("Enregistrer l’application", "Register application")}
            </button>
          </form>
        )}
      </section>
      {apps.some((a) => a.enabled === true) && (
        <section className="bp-card bp-stack">
          <h2>
            {t(
              "Préparer un lien d’installation",
              "Prepare an installation link",
            )}
          </h2>
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/mobile/deferred/" + String(d.get("appId")), {
                platform: d.get("platform"),
                destination: d.get("destination"),
                consent: d.get("consent") === "on",
              }),
            )}
          >
            <label className="bp-field">
              Application
              <select name="appId">
                {apps
                  .filter((a) => a.enabled === true)
                  .map((a) => (
                    <option key={String(a.id)} value={String(a.id)}>
                      {String(a.name)}
                    </option>
                  ))}
              </select>
            </label>
            <label className="bp-field">
              {t("Plateforme", "Platform")}
              <select name="platform">
                <option value="android">Android</option>
                <option value="ios">iOS</option>
              </select>
            </label>
            <Field
              name="destination"
              label={t(
                "Destination dans l’application (ex. monapp://produit/42)",
                "In-app destination (e.g. myapp://product/42)",
              )}
              required
            />
            <label>
              <input name="consent" type="checkbox" required />{" "}
              {t(
                "J’accepte de conserver cette destination pour la retrouver après l’installation.",
                "I agree to preserve this destination to retrieve it after installation.",
              )}
            </label>
            <button className="bp-btn bp-btn--primary" disabled={busy}>
              {t("Préparer l’installation", "Prepare installation")}
            </button>
          </form>
          {Boolean(handoff.token) && (
            <div className="bp-stack">
              <a
                className="bp-btn bp-btn--secondary"
                href={String(handoff.storeUrl)}
                rel="noreferrer"
              >
                {t("Ouvrir la boutique", "Open store")}
              </a>
              <p>
                {t(
                  "Sur iOS, conservez ce code et saisissez-le dans l’application après installation.",
                  "On iOS, save this code and enter it in the application after installation.",
                )}
              </p>
              <code className="break">{String(handoff.token)}</code>
              <button
                className="bp-btn bp-btn--secondary"
                onClick={() => {
                  void navigator.clipboard
                    .writeText(String(handoff.token))
                    .then(() => setMessage(t("Code copié.", "Code copied.")))
                    .catch(() =>
                      setMessage(
                        t(
                          "Sélectionnez le code pour le copier.",
                          "Select the code to copy it.",
                        ),
                      ),
                    );
                }}
              >
                {t("Copier le code", "Copy code")}
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
