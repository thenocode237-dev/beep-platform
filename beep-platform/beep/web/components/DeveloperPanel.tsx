"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { send } from "@/lib/api";

export type DeveloperView = {
  keys: {
    id: string;
    name: string;
    prefix: string;
    scopes: string[];
    origins: string[];
    publicKey: boolean;
    revokedAt: string | null;
  }[];
  tracking: {
    linkId: string;
    enabled: boolean;
    model: string;
    windowDays: number;
    pixels: Record<string, string>;
  }[];
  webhooks: { id: string; url: string; events: string[]; enabled: boolean }[];
  deliveries: {
    id: string;
    event: string;
    status: string;
    attempts: number;
    httpStatus: number | null;
    createdAt: string;
  }[];
};
export type ConversionView = {
  funnel: {
    linkId: string;
    slug: string;
    clicks: number;
    leads: number;
    sales: number;
  }[];
  revenue: { linkId: string | null; currency: string; revenue: number }[];
  pages: { page: string; views: number; visitors: number }[];
  campaigns?: {
    source: string | null;
    campaign: string | null;
    currency: string | null;
    events: number;
    revenue: number | null;
  }[];
  usage?: { count: number }[];
};
export function DeveloperPanel({
  view,
  analytics,
  links,
  entitlements,
  en,
  days = 30,
}: {
  view: DeveloperView;
  analytics?: ConversionView;
  links: { id: string; slug: string }[];
  entitlements: Record<string, number>;
  en: boolean;
  days?: number;
}) {
  const router = useRouter(),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [secret, setSecret] = useState(""),
    [publicKey, setPublicKey] = useState(false),
    [selected, setSelected] = useState(links[0]?.id ?? "");
  const txt = (fr: string, english: string) => (en ? english : fr),
    conversion = entitlements["features.conversion"] === 1;
  async function run(
    method: "POST" | "PUT" | "DELETE",
    path: string,
    body?: unknown,
  ) {
    setBusy(true);
    setMessage("");
    try {
      const r = await send<{ token?: string; secret?: string }>(
        method,
        path,
        body,
      );
      if (!r.ok) {
        setMessage(
          {
            feature_unavailable: txt(
              "Cette fonction nécessite une offre compatible.",
              "This feature requires an eligible plan.",
            ),
            invalid_request: txt(
              "Vérifiez les champs saisis.",
              "Check the form fields.",
            ),
            quota_exceeded: txt("Le quota est atteint.", "Quota reached."),
            conflict: txt(
              "Cette livraison ne peut pas être rejouée dans son état actuel.",
              "This delivery cannot be replayed in its current state.",
            ),
          }[r.code ?? ""] ??
            txt(
              "L’action a échoué. Réessayez.",
              "Action failed. Please try again.",
            ),
        );
        return;
      }
      if (r.data?.token || r.data?.secret)
        setSecret(r.data.token ?? r.data.secret ?? "");
      else setMessage(txt("Modification enregistrée.", "Changes saved."));
      router.refresh();
    } finally {
      setBusy(false);
    }
  }
  const config = view.tracking.find((t) => t.linkId === selected);
  const form = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    return new FormData(event.currentTarget);
  };
  return (
    <div className="bp-stack bp-stack--lg">
      <header className="page-heading">
        <p className="eyebrow">Beep / API</p>
        <h1>
          {txt("Conversions & développeurs.", "Conversions & developers.")}
        </h1>
        <p>
          {txt(
            "Reliez vos partages aux résultats, puis connectez vos outils.",
            "Connect your shares to results, then connect your tools.",
          )}
        </p>
        <a href={en ? "/en/docs" : "/docs"}>
          {txt(
            "Documentation et exemples API",
            "API documentation and examples",
          )}
        </a>
      </header>
      {conversion && (
        <nav
          className="bp-row"
          aria-label={txt("Période des statistiques", "Statistics period")}
        >
          {[7, 30, 90, 365, 36500].map((period) => (
            <a
              className="bp-btn bp-btn--secondary"
              key={period}
              href={`?days=${period}`}
              aria-current={period === days ? "page" : undefined}
            >
              {period === 36500
                ? txt("Depuis le début", "All time")
                : `${period} ${txt("jours", "days")}`}
            </a>
          ))}
        </nav>
      )}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {secret && (
        <section className="bp-card bp-stack">
          <h2>
            {txt("Copiez votre secret maintenant", "Copy your secret now")}
          </h2>
          <p>
            {txt(
              "Il ne sera plus affiché après fermeture. Une clé pk_ peut être utilisée dans votre site ; les clés sk_ et les secrets de signature restent côté serveur.",
              "It will not be shown again. A pk_ key can be used on your website; sk_ keys and signing secrets stay on your server.",
            )}
          </p>
          <code className="break">{secret}</code>
          {secret.startsWith("pk_") && (
            <pre className="developer-code">{`<script>window.beep=window.beep||function(){(window.beep.q=window.beep.q||[]).push(arguments)};</script>\n<script defer src="${typeof window !== "undefined" ? window.location.origin : ""}/beep.js" data-key="${secret}"></script>\n// Après accord dans votre bannière de consentement :\nbeep('consent', true);\nbeep('track', 'sale', {value: 49, currency: 'EUR'});`}</pre>
          )}
          <button
            className="bp-btn bp-btn--secondary"
            onClick={() => setSecret("")}
          >
            {txt("J’ai enregistré le secret", "I saved the secret")}
          </button>
        </section>
      )}
      <section className="bp-card bp-stack">
        <h2>{txt("Clés API", "API keys")}</h2>
        <p>
          {txt(
            "Pro : 60 requêtes/min. Elite : 600. Chaque clé est limitée à cet espace et aux portées choisies.",
            "Pro: 60 requests/min. Elite: 600. Each key is limited to this workspace and its selected scopes.",
          )}
        </p>
        <form
          className="bp-stack"
          onSubmit={(e) => {
            const f = form(e);
            void run("POST", "/developer/keys", {
              name: f.get("name"),
              publicKey,
              scopes: publicKey ? ["events"] : f.getAll("scopes"),
              origins: String(f.get("origins") ?? "")
                .split(/\s+/)
                .filter(Boolean),
            });
          }}
        >
          <label>
            {txt("Nom de la clé", "Key name")}
            <input className="bp-input" name="name" required maxLength={80} />
          </label>
          {conversion && (
            <label>
              <input
                type="checkbox"
                checked={publicKey}
                onChange={(e) => setPublicKey(e.target.checked)}
              />{" "}
              {txt("Clé publique pour beep.js", "Public key for beep.js")}
            </label>
          )}
          {publicKey ? (
            <label>
              {txt(
                "Origines autorisées (une par ligne, sans chemin)",
                "Allowed origins (one per line, no path)",
              )}
              <textarea
                className="bp-input"
                name="origins"
                required
                placeholder="https://shop.example.com"
              />
            </label>
          ) : (
            <fieldset className="bp-row">
              <legend>{txt("Portées", "Scopes")}</legend>
              {["read", "write", "events"].map((s) => (
                <label key={s}>
                  <input
                    type="checkbox"
                    name="scopes"
                    value={s}
                    defaultChecked={s === "read"}
                  />{" "}
                  {s}
                </label>
              ))}
            </fieldset>
          )}
          <button
            disabled={busy || (entitlements["api.rpm"] ?? 0) === 0}
            className="bp-btn bp-btn--primary"
          >
            {busy
              ? txt("En cours…", "Working…")
              : txt("Créer une clé", "Create key")}
          </button>
        </form>
        {view.keys.length === 0 && (
          <p>{txt("Aucune clé créée.", "No keys yet.")}</p>
        )}
        {view.keys.map((k) => (
          <article className="bp-row bp-row--between" key={k.id}>
            <div>
              <strong>{k.name}</strong> <code>{k.prefix}…</code>
              <p>
                {k.scopes.join(", ")}
                {k.revokedAt ? txt(" · Révoquée", " · Revoked") : ""}
              </p>
            </div>
            {!k.revokedAt && (
              <button
                disabled={busy}
                className="bp-btn bp-btn--secondary"
                onClick={() => void run("DELETE", `/developer/keys/${k.id}`)}
              >
                {txt("Révoquer", "Revoke")}
              </button>
            )}
          </article>
        ))}
      </section>
      {!conversion && (
        <p className="bp-card">
          {txt(
            "Le suivi de conversion, les analytics de site et les webhooks sont disponibles avec Elite.",
            "Conversion tracking, website analytics and webhooks are available with Elite.",
          )}
        </p>
      )}
      {conversion && (
        <>
          <section className="bp-card bp-stack">
            <h2>{txt("Suivi par lien", "Tracking by link")}</h2>
            {links.length === 0 ? (
              <p>
                {txt(
                  "Créez d’abord un lien pour activer son suivi.",
                  "Create a link first to enable tracking.",
                )}
              </p>
            ) : (
              <>
                <label>
                  {txt("Lien", "Link")}
                  <select
                    className="bp-input"
                    value={selected}
                    onChange={(e) => setSelected(e.target.value)}
                  >
                    {links.map((l) => (
                      <option key={l.id} value={l.id}>
                        /{l.slug}
                      </option>
                    ))}
                  </select>
                </label>
                <form
                  key={selected + JSON.stringify(config)}
                  className="bp-stack"
                  onSubmit={(e) => {
                    const f = form(e),
                      pixels: Record<string, string> = {};
                    for (const p of ["meta", "google", "tiktok"]) {
                      const v = String(f.get(p) ?? "").trim();
                      if (v) pixels[p] = v;
                    }
                    void run("PUT", `/conversions/links/${selected}`, {
                      enabled: f.has("enabled"),
                      model: f.get("model"),
                      windowDays: Number(f.get("days")),
                      pixels,
                    });
                  }}
                >
                  <label>
                    <input
                      type="checkbox"
                      name="enabled"
                      defaultChecked={config?.enabled ?? false}
                    />{" "}
                    {txt(
                      "Activer le suivi sur ce lien",
                      "Enable tracking on this link",
                    )}
                  </label>
                  <label>
                    {txt("Attribution", "Attribution")}
                    <select
                      className="bp-input"
                      name="model"
                      defaultValue={config?.model ?? "last"}
                    >
                      <option value="last">
                        {txt("Dernier clic", "Last click")}
                      </option>
                      <option value="first">
                        {txt("Premier clic", "First click")}
                      </option>
                    </select>
                  </label>
                  <label>
                    {txt("Fenêtre en jours (1–90)", "Window in days (1–90)")}
                    <input
                      className="bp-input"
                      name="days"
                      type="number"
                      min={1}
                      max={90}
                      defaultValue={config?.windowDays ?? 30}
                      required
                    />
                  </label>
                  <details>
                    <summary>
                      {txt(
                        "Pixels publicitaires facultatifs",
                        "Optional advertising pixels",
                      )}
                    </summary>
                    <p>
                      {txt(
                        "Une page de choix précède la destination. Aucun pixel tiers ne se charge avant l’accord du visiteur.",
                        "A consent page appears before the destination. Third-party pixels only load after the visitor agrees.",
                      )}
                    </p>
                    {["meta", "google", "tiktok"].map((p) => (
                      <label className="bp-stack" key={p}>
                        {p}
                        <input
                          className="bp-input"
                          name={p}
                          defaultValue={config?.pixels[p] ?? ""}
                          maxLength={64}
                          placeholder={
                            p === "google" ? "AW-123456789" : "Pixel ID"
                          }
                        />
                      </label>
                    ))}
                  </details>
                  <button className="bp-btn bp-btn--primary" disabled={busy}>
                    {txt("Enregistrer", "Save")}
                  </button>
                </form>
              </>
            )}
          </section>
          <section className="bp-card bp-stack">
            <h2>{txt("Du clic au revenu", "From click to revenue")}</h2>
            {!analytics?.funnel.length ? (
              <p>
                {txt(
                  "Les résultats apparaîtront après vos premières visites et conversions.",
                  "Results will appear after your first visits and conversions.",
                )}
              </p>
            ) : (
              analytics.funnel.map((f) => (
                <article className="bp-card" key={f.linkId}>
                  <h3>/{f.slug}</h3>
                  <p>
                    {f.clicks} {txt("clics", "clicks")} → {f.leads} leads →{" "}
                    {f.sales} {txt("ventes", "sales")}
                  </p>
                  <p>
                    {txt("Clic → lead : ", "Click → lead: ")}
                    {f.clicks ? ((f.leads / f.clicks) * 100).toFixed(1) : "0"} %
                    · {txt("Lead → vente : ", "Lead → sale: ")}
                    {f.leads ? ((f.sales / f.leads) * 100).toFixed(1) : "0"} % ·{" "}
                    {txt("Clic → vente : ", "Click → sale: ")}
                    {f.clicks ? ((f.sales / f.clicks) * 100).toFixed(1) : "0"} %
                  </p>
                  {analytics.revenue
                    .filter((r) => r.linkId === f.linkId)
                    .map((r) => (
                      <p key={r.currency}>
                        {Number(r.revenue).toLocaleString(en ? "en" : "fr")}{" "}
                        {r.currency} ·{" "}
                        {txt("valeur par clic", "value per click")}{" "}
                        {f.clicks
                          ? (Number(r.revenue) / f.clicks).toFixed(2)
                          : "0"}{" "}
                        {r.currency}
                      </p>
                    ))}
                </article>
              ))
            )}
            <h3>{txt("Pages vues", "Page views")}</h3>
            {analytics?.pages.map((p) => (
              <p className="break" key={p.page}>
                {p.page} · {p.views} {txt("vues", "views")} · {p.visitors}{" "}
                {txt(
                  "visiteurs quotidiens cumulés avec consentement",
                  "summed daily consenting visitors",
                )}
              </p>
            ))}
            <LiveEvents en={en} />
          </section>
          <section className="bp-card bp-stack">
            <h2>Webhooks</h2>
            <form
              className="bp-stack"
              onSubmit={(e) => {
                const f = form(e);
                void run("POST", "/webhooks", {
                  url: f.get("url"),
                  events: f.getAll("events"),
                });
              }}
            >
              <label>
                URL HTTPS
                <input
                  className="bp-input"
                  type="url"
                  name="url"
                  placeholder="https://example.com/webhooks/beep"
                  required
                  maxLength={2048}
                />
              </label>
              <fieldset className="bp-row">
                <legend>{txt("Événements", "Events")}</legend>
                {[
                  "click",
                  "lead",
                  "sale",
                  "signup",
                  "subscription",
                  "link.created",
                  "domain.status_changed",
                ].map((s) => (
                  <label key={s}>
                    <input
                      type="checkbox"
                      name="events"
                      value={s}
                      defaultChecked={s === "sale"}
                    />{" "}
                    {s}
                  </label>
                ))}
              </fieldset>
              <button className="bp-btn bp-btn--primary" disabled={busy}>
                {txt("Ajouter un webhook", "Add webhook")}
              </button>
            </form>
            {view.webhooks.map((w) => (
              <article className="bp-stack" key={w.id}>
                <p className="break">
                  {w.url} · {w.events.join(", ")}
                </p>
                {w.enabled ? (
                  <button
                    className="bp-btn bp-btn--secondary"
                    disabled={busy}
                    onClick={() => void run("DELETE", `/webhooks/${w.id}`)}
                  >
                    {txt("Désactiver", "Disable")}
                  </button>
                ) : (
                  <p>{txt("Désactivé", "Disabled")}</p>
                )}
              </article>
            ))}
          </section>
          <section className="bp-card bp-stack">
            <h2>{txt("Journal des livraisons", "Delivery log")}</h2>
            {view.deliveries.length === 0 && (
              <p>
                {txt("Aucune livraison pour le moment.", "No deliveries yet.")}
              </p>
            )}
            {view.deliveries.map((d) => (
              <article className="bp-card bp-stack" key={d.id}>
                <p>
                  {d.event} · {d.status} · {d.attempts}{" "}
                  {txt("tentative(s)", "attempt(s)")} · HTTP{" "}
                  {d.httpStatus ?? "—"}
                </p>
                <time>
                  {new Date(d.createdAt).toLocaleString(en ? "en" : "fr")}
                </time>
                {["failed", "delivered"].includes(d.status) && (
                  <button
                    className="bp-btn bp-btn--secondary"
                    disabled={busy}
                    onClick={() =>
                      void run("POST", `/webhooks/deliveries/${d.id}/replay`)
                    }
                  >
                    {txt("Rejouer", "Replay")}
                  </button>
                )}
              </article>
            ))}
          </section>
        </>
      )}
      {conversion && (
        <section className="bp-card bp-stack">
          <h2>{txt("Campagnes & consommation", "Campaigns & usage")}</h2>
          <p>
            {analytics?.usage?.[0]?.count ?? 0} /{" "}
            {entitlements["events.monthly_max"] ?? 100000}{" "}
            {txt("événements sur la période", "events in this period")}
          </p>
          {analytics?.campaigns?.length ? (
            analytics.campaigns.map((c, i) => (
              <p key={i}>
                {c.source ?? "—"} / {c.campaign ?? "—"} · {c.events}{" "}
                {txt("événements", "events")} · {c.revenue ?? 0}{" "}
                {c.currency ?? ""}
              </p>
            ))
          ) : (
            <p>
              {txt(
                "Les paramètres UTM de vos pages alimenteront cette vue.",
                "Your page UTM parameters will populate this view.",
              )}
            </p>
          )}
        </section>
      )}
      {conversion && <Postbacks en={en} busy={busy} run={run} />}
    </div>
  );
}
import { useEffect } from "react";
function Postbacks({
  en,
  busy,
  run,
}: {
  en: boolean;
  busy: boolean;
  run: (
    method: "POST" | "PUT" | "DELETE",
    path: string,
    body?: unknown,
  ) => Promise<void>;
}) {
  const [provider, setProvider] = useState("meta");
  return (
    <section className="bp-card bp-stack">
      <h2>Postbacks Meta / Google</h2>
      <p>
        {en
          ? "Send leads and sales as server events. Provider secrets are encrypted. Google receives standalone GA4 events; this does not link an existing Google browser session."
          : "Transmettez les leads et ventes comme événements serveur. Les secrets sont chiffrés. Google reçoit des événements GA4 autonomes ; ce parcours ne les raccorde pas à une session Google existante."}
      </p>
      <form
        className="bp-stack"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          void run("POST", "/postbacks", {
            provider,
            account: f.get("account"),
            token: f.get("token"),
            version: f.get("version"),
          });
        }}
      >
        <label>
          {en ? "Provider" : "Fournisseur"}
          <select
            className="bp-input"
            value={provider}
            onChange={(e) => setProvider(e.target.value)}
          >
            <option value="meta">Meta CAPI</option>
            <option value="google">Google GA4</option>
          </select>
        </label>
        <label>
          {provider === "meta" ? "Pixel ID" : "Measurement ID (G-…)"}
          <input className="bp-input" name="account" required maxLength={32} />
        </label>
        <label>
          {provider === "meta" ? "Access token" : "API secret"}
          <input
            className="bp-input"
            name="token"
            type="password"
            autoComplete="off"
            required
            maxLength={2048}
          />
        </label>
        {provider === "meta" && (
          <label>
            {en ? "Graph API version" : "Version Graph API"}
            <input
              className="bp-input"
              name="version"
              placeholder="vXX.0"
              pattern="v[0-9]{1,3}\.[0-9]{1,2}"
              required
            />
          </label>
        )}
        <p>
          {en
            ? "Only consenting events with a visitor reference are delivered. Review delivery results in the log above."
            : "Seuls les événements consentis avec une référence visiteur sont livrés. Consultez le résultat dans le journal ci-dessus."}
        </p>
        <button className="bp-btn bp-btn--primary" disabled={busy}>
          {en ? "Connect postback" : "Connecter le postback"}
        </button>
      </form>
    </section>
  );
}
function LiveEvents({ en }: { en: boolean }) {
  const [events, setEvents] = useState<
      { id: string; name: string; at: string }[]
    >([]),
    [status, setStatus] = useState("");
  useEffect(() => {
    let cursor = new Date(Date.now() - 60000).toISOString();
    let cancelled = false;
    let source: EventSource;
    function connect() {
      source = new EventSource(
        `/v1/conversions/stream?after=${encodeURIComponent(cursor)}`,
      );
      source.onopen = () => setStatus(en ? "Connected" : "Connecté");
      for (const name of ["conversion"]) {
        source.addEventListener(name, (e) => {
          try {
            const event = JSON.parse((e as MessageEvent).data) as {
              id: string;
              name: string;
              at: string;
            };
            cursor = event.at;
            setEvents((old) =>
              [event, ...old.filter((x) => x.id !== event.id)].slice(0, 20),
            );
          } catch {}
        });
      }
      source.onerror = () => {
        source.close();
        setStatus(en ? "Reconnecting…" : "Reconnexion…");
        if (!cancelled)
          setTimeout(() => {
            if (!cancelled) connect();
          }, 5000);
      };
    }
    connect();
    return () => {
      cancelled = true;
      source?.close();
    };
  }, [en]);
  return (
    <details>
      <summary>
        {en ? "Live events" : "Événements en direct"} · {status}
      </summary>
      {events.length ? (
        events.map((e) => (
          <p key={e.id}>
            {e.name} · {new Date(e.at).toLocaleTimeString()}
          </p>
        ))
      ) : (
        <p>{en ? "Waiting for events…" : "En attente d’événements…"}</p>
      )}
    </details>
  );
}
