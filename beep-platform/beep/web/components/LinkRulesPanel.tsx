"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { send, type LinkView, type RoutingRule } from "@/lib/api";
import { href, type Locale } from "@/lib/i18n";

export function LinkRulesPanel({
  link,
  locale,
  entitlements,
}: {
  link: LinkView;
  locale: Locale;
  entitlements: Record<string, number>;
}) {
  const en = locale === "en",
    router = useRouter(),
    r = link.rules;
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState(false);
  const [routes, setRoutes] = useState<RoutingRule[]>(r.routes);
  const [variants, setVariants] = useState(r.variants);
  const pro = entitlements["features.link_rules"] === 1,
    elite = entitlements["features.link_routing"] === 1,
    utm = entitlements["features.utm"] === 1;
  const text = (fr: string, english: string) => (en ? english : fr);
  const split = (s: string) =>
    s
      .split(",")
      .map((x) => x.trim())
      .filter(Boolean);
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setMessage("");
    const d = new FormData(e.currentTarget),
      str = (key: string) => String(d.get(key) ?? "").trim() || null;
    const date = str("expiresAt");
    const result = await send("PATCH", `/links/${link.id}`, {
      rules: {
        expiresAt: pro
          ? date
            ? new Date(date).toISOString()
            : null
          : r.expiresAt,
        password: pro ? (d.get("removePassword") ? "" : str("password")) : null,
        deeplink: d.has("deeplink"),
        onelink: {
          ios: str("ios"),
          android: str("android"),
          desktop: str("desktop"),
        },
        routes,
        variants,
        utm: utm
          ? {
              source: str("source"),
              medium: str("medium"),
              campaign: str("campaign"),
              term: str("term"),
              content: str("content"),
            }
          : r.utm,
      },
    });
    setBusy(false);
    setError(!result.ok);
    setMessage(
      result.ok
        ? text("Règles enregistrées.", "Rules saved.")
        : result.code === "feature_unavailable"
          ? text(
              "Cette fonction n’est pas disponible avec votre offre ou a été désactivée.",
              "This feature is unavailable on your plan or has been disabled.",
            )
          : text(
              "Vérifiez les URL, les dates et les critères, puis réessayez.",
              "Check URLs, dates and conditions, then try again.",
            ),
    );
    if (result.ok) router.refresh();
  }
  const localDate = r.expiresAt
    ? new Date(
        new Date(r.expiresAt).getTime() -
          new Date(r.expiresAt).getTimezoneOffset() * 60000,
      )
        .toISOString()
        .slice(0, 16)
    : "";
  return (
    <section
      className="bp-card bp-card--lg bp-stack"
      aria-labelledby="rules-title"
    >
      <h2 id="rules-title">
        {text("Règles de redirection", "Redirect rules")}
      </h2>
      <p className="muted">
        {text(
          "Ciblage → test A/B → Onelink → destination principale. Les UTM existants sont conservés.",
          "Targeting → A/B test → Onelink → primary destination. Existing UTM parameters are preserved.",
        )}
      </p>
      <a className="text-link" href={href(locale, "/app/simulator")}>
        {text("Ouvrir le simulateur d’applications", "Open the app simulator")}
      </a>
      <form onSubmit={save} className="bp-stack">
        <label className="bp-row">
          <input type="checkbox" name="deeplink" defaultChecked={r.deeplink} />
          {text(
            "Ouvrir l’application quand elle est disponible",
            "Open the app when available",
          )}
        </label>
        <fieldset className="bp-stack">
          <legend>Onelink</legend>
          {(["ios", "android", "desktop"] as const).map((key) => (
            <label className="bp-field" key={key}>
              {key === "desktop"
                ? text("Ordinateur", "Desktop")
                : key === "ios"
                  ? "iOS"
                  : "Android"}
              <input
                name={key}
                type="url"
                defaultValue={r.onelink?.[key] ?? ""}
                placeholder="https://…"
              />
            </label>
          ))}
        </fieldset>
        <fieldset className="bp-stack" disabled={!pro}>
          <legend>
            {text(
              "Expiration et mot de passe · Pro",
              "Expiry and password · Pro",
            )}
          </legend>
          {!pro && (
            <p className="muted">
              {text("Disponible avec Pro.", "Available with Pro.")}
            </p>
          )}
          <label className="bp-field">
            {text("Expiration (heure locale)", "Expiry (local time)")}
            <input
              name="expiresAt"
              type="datetime-local"
              defaultValue={localDate}
            />
          </label>
          <label className="bp-field">
            {text(
              "Nouveau mot de passe (vide = inchangé)",
              "New password (empty = unchanged)",
            )}
            <input
              name="password"
              type="password"
              minLength={4}
              maxLength={200}
              autoComplete="new-password"
            />
          </label>
          {link.passwordProtected && (
            <label className="bp-row">
              <input type="checkbox" name="removePassword" />
              {text("Retirer le mot de passe", "Remove password")}
            </label>
          )}
        </fieldset>
        <fieldset className="bp-stack" disabled={!utm}>
          <legend>UTM · Pro</legend>
          {(["source", "medium", "campaign", "term", "content"] as const).map(
            (key) => (
              <label className="bp-field" key={key}>
                {key}
                <input
                  name={key}
                  maxLength={100}
                  defaultValue={r.utm?.[key] ?? ""}
                />
              </label>
            ),
          )}
        </fieldset>
        <fieldset className="bp-stack" disabled={!elite}>
          <legend>{text("Ciblage · Elite", "Targeting · Elite")}</legend>
          <p className="muted small">
            {text(
              "Première règle correspondante. Séparez les valeurs par des virgules ; vide = tous.",
              "First matching rule. Separate values with commas; empty = all.",
            )}
          </p>
          {routes.map((route, i) => (
            <div className="bp-card bp-stack" key={i}>
              <label className="bp-field">
                URL
                <input
                  type="url"
                  required
                  value={route.url}
                  onChange={(e) =>
                    setRoutes(
                      routes.map((x, n) =>
                        n === i ? { ...x, url: e.target.value } : x,
                      ),
                    )
                  }
                />
              </label>
              {(["countries", "os", "devices", "languages"] as const).map(
                (key) => (
                  <label className="bp-field" key={key}>
                    {
                      {
                        countries: text("Pays (CM, FR)", "Countries (CM, FR)"),
                        os: "OS (ios, android, windows, macos, linux, chromeos)",
                        devices: text(
                          "Appareils (mobile, tablet, desktop)",
                          "Devices (mobile, tablet, desktop)",
                        ),
                        languages: text(
                          "Langues (fr, en)",
                          "Languages (fr, en)",
                        ),
                      }[key]
                    }
                    <input
                      defaultValue={route[key].join(", ")}
                      onBlur={(e) =>
                        setRoutes(
                          routes.map((x, n) =>
                            n === i
                              ? { ...x, [key]: split(e.target.value) }
                              : x,
                          ),
                        )
                      }
                    />
                  </label>
                ),
              )}
              <button
                type="button"
                className="bp-btn bp-btn--ghost"
                onClick={() => setRoutes(routes.filter((_, n) => n !== i))}
              >
                {text("Retirer cette règle", "Remove rule")}
              </button>
            </div>
          ))}
          <button
            type="button"
            className="bp-btn bp-btn--secondary"
            disabled={routes.length >= 10}
            onClick={() =>
              setRoutes([
                ...routes,
                { url: "", countries: [], os: [], devices: [], languages: [] },
              ])
            }
          >
            {text("Ajouter une règle", "Add rule")}
          </button>
        </fieldset>
        <fieldset className="bp-stack" disabled={!elite}>
          <legend>
            {text("Test A/B pondéré · Elite", "Weighted A/B test · Elite")}
          </legend>
          {variants.map((v, i) => (
            <div className="bp-card bp-stack" key={i}>
              <label className="bp-field">
                URL
                <input
                  type="url"
                  required
                  value={v.url}
                  onChange={(e) =>
                    setVariants(
                      variants.map((x, n) =>
                        n === i ? { ...x, url: e.target.value } : x,
                      ),
                    )
                  }
                />
              </label>
              <label className="bp-field">
                {text("Poids", "Weight")}
                <input
                  type="number"
                  min={1}
                  max={1000}
                  required
                  value={v.weight}
                  onChange={(e) =>
                    setVariants(
                      variants.map((x, n) =>
                        n === i ? { ...x, weight: Number(e.target.value) } : x,
                      ),
                    )
                  }
                />
              </label>
              <button
                type="button"
                className="bp-btn bp-btn--ghost"
                onClick={() => setVariants(variants.filter((_, n) => n !== i))}
              >
                {text("Retirer la variante", "Remove variant")}
              </button>
            </div>
          ))}
          <button
            type="button"
            className="bp-btn bp-btn--secondary"
            disabled={variants.length >= 5}
            onClick={() => setVariants([...variants, { url: "", weight: 50 }])}
          >
            {text("Ajouter une variante", "Add variant")}
          </button>
        </fieldset>
        {message && <p role={error ? "alert" : "status"}>{message}</p>}
        <button className="bp-btn" disabled={busy}>
          {busy
            ? text("Enregistrement…", "Saving…")
            : text("Enregistrer les règles", "Save rules")}
        </button>
      </form>
    </section>
  );
}
