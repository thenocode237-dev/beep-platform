"use client";
import {
  useEffect,
  useId,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { send } from "@/lib/api";

export type Row = Record<string, unknown>;
const text = (r: Row, k: string) => String(r[k] ?? "");
const rows = (r: Row, k: string): Row[] =>
  Array.isArray(r[k]) ? (r[k] as Row[]) : [];
function Field({
  label,
  name,
  type = "text",
  required = false,
  children,
  ...props
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  children?: ReactNode;
  maxLength?: number;
  placeholder?: string;
  defaultValue?: string;
  min?: number;
  max?: number;
}) {
  const id = useId();
  return (
    <label className="bp-field" htmlFor={id}>
      <span className="bp-field__label">{label}</span>
      {children ? (
        <select
          id={id}
          name={name}
          className="bp-input"
          required={required}
          {...props}
        >
          {children}
        </select>
      ) : (
        <input
          id={id}
          name={name}
          type={type}
          required={required}
          className="bp-input"
          {...props}
        />
      )}
    </label>
  );
}
function Table({
  items,
  columns,
}: {
  items: Row[];
  columns: [string, string][];
}) {
  return (
    <div className="eco-records">
      {items.map((r, i) => (
        <article className="bp-card" key={text(r, "id") || i}>
          <dl className="eco-dl">
            {columns.map(([key, label]) => (
              <div key={key}>
                <dt>{label}</dt>
                <dd className="break">{text(r, key) || "—"}</dd>
              </div>
            ))}
          </dl>
        </article>
      ))}
    </div>
  );
}
export const sections = {
  team: { fr: "Votre équipe", en: "Your team", path: "/team" },
  organization: {
    fr: "Organiser et importer",
    en: "Organize and import",
    path: "/organization",
  },
  integrations: {
    fr: "Intégrations",
    en: "Integrations",
    path: "/integrations",
  },
  radar: { fr: "Radar", en: "Radar", path: "/radar" },
  insights: {
    fr: "Customer insights",
    en: "Customer insights",
    path: "/insights",
  },
  affiliates: { fr: "Affiliation", en: "Affiliates", path: "/affiliates" },
  "bio-submissions": {
    fr: "Réponses des pages bio",
    en: "Bio page responses",
    path: "/bio-submissions",
  },
} as const;
export type Section = keyof typeof sections;

export function EcosystemPanel({
  section,
  initial,
  team,
  entitlements,
  en,
  invite = "",
  status,
}: {
  section: Section;
  initial: Row;
  team: Row;
  entitlements: Record<string, number>;
  en: boolean;
  invite?: string;
  status: number;
}) {
  const [origin, setOrigin] = useState("");
  useEffect(() => setOrigin(window.location.origin), []);
  const [view, setView] = useState(initial),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [secret, setSecret] = useState(""),
    [result, setResult] = useState<Row[]>([]),
    [provider, setProvider] = useState("stripe"),
    [channel, setChannel] = useState("email"),
    [filter, setFilter] = useState(""),
    [stream, setStream] = useState(false);
  const t = (fr: string, english: string) => (en ? english : fr),
    admin = ["owner", "admin"].includes(text(team, "role")),
    writable = text(team, "role") !== "viewer",
    path = sections[section].path;
  async function refresh() {
    const r = await send<Row>("GET", path);
    if (r.ok && r.data) setView(r.data);
    else
      setError(
        t(
          "Les données sont indisponibles. Réessayez.",
          "Data is unavailable. Please try again.",
        ),
      );
  }
  async function run(
    method: "POST" | "PUT" | "PATCH" | "DELETE",
    url: string,
    body?: unknown,
  ) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const r = await send<Row>(method, url, body);
      if (!r.ok) {
        setError(
          r.status === 403
            ? t(
                "Votre rôle ou votre offre ne permet pas cette action.",
                "Your role or plan does not allow this action.",
              )
            : r.code === "conflict" || r.code === "quota_reached"
              ? t(
                  "Cette action est en conflit ou le quota est atteint. Actualisez les données.",
                  "This action conflicts with existing data or the quota has been reached. Refresh your data.",
                )
              : t(
                  "L’action a échoué. Vérifiez les champs puis réessayez.",
                  "The action failed. Check the fields and try again.",
                ),
        );
        return false;
      }
      if (r.data?.signingSecret) setSecret(String(r.data.signingSecret));
      if (Array.isArray(r.data?.items)) setResult(r.data.items as Row[]);
      setNotice(t("Modification enregistrée.", "Changes saved."));
      await refresh();
      return true;
    } finally {
      setBusy(false);
    }
  }
  function form(fn: (data: FormData) => Promise<boolean>) {
    return async (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      const f = e.currentTarget;
      if (await fn(new FormData(f))) f.reset();
    };
  }
  const btn = (label: string) => (
    <button className="bp-btn bp-btn--primary" disabled={busy} type="submit">
      {busy ? t("Enregistrement…", "Saving…") : label}
    </button>
  );
  useEffect(() => {
    if (section !== "radar" || !entitlements["features.insights"]) return;
    const s = new EventSource("/v1/radar/stream");
    s.addEventListener("radar", (event) => {
      try {
        setView(JSON.parse((event as MessageEvent).data) as Row);
        setStream(true);
      } catch {
        setStream(false);
      }
    });
    s.onerror = () => setStream(false);
    return () => s.close();
  }, [section, entitlements]);
  return (
    <div className="app-page bp-stack bp-stack--lg">
      <header className="app-heading">
        <p className="eyebrow">{t("Votre workspace", "Your workspace")}</p>
        <h1>{sections[section][en ? "en" : "fr"]}</h1>
        <p>
          {t(
            "Un espace partagé, des actions qui restent sous votre contrôle.",
            "A shared workspace with actions that stay under your control.",
          )}
        </p>
      </header>
      {error && (
        <div className="bp-card" role="alert">
          {error}
        </div>
      )}
      {notice && <p role="status">{notice}</p>}
      {status === 403 && (
        <div className="bp-card">
          <p>
            {t(
              "Cette rubrique nécessite une offre ou un rôle adapté.",
              "This section requires an eligible plan or role.",
            )}
          </p>
          <a
            className="text-link"
            href={en ? "/en/app/billing" : "/app/billing"}
          >
            {t("Voir mon offre", "View my plan")}
          </a>
        </div>
      )}
      {secret && (
        <section
          className="bp-card bp-stack"
          aria-label={t("Secret de signature", "Signing secret")}
        >
          <p>
            {t(
              "Copiez ce secret maintenant : il ne sera plus affiché.",
              "Copy this secret now: it will not be shown again.",
            )}
          </p>
          <code className="developer-code">{secret}</code>
          <button
            className="bp-btn bp-btn--secondary"
            onClick={() => {
              void navigator.clipboard
                .writeText(secret)
                .then(() => setNotice(t("Secret copié.", "Secret copied.")))
                .catch(() =>
                  setError(
                    t(
                      "Sélectionnez le secret et copiez-le.",
                      "Select the secret and copy it.",
                    ),
                  ),
                );
            }}
          >
            {t("Copier", "Copy")}
          </button>
          <button
            className="bp-btn bp-btn--ghost"
            onClick={() => setSecret("")}
          >
            {t("Masquer", "Hide")}
          </button>
        </section>
      )}
      {section === "team" && (
        <>
          <section className="bp-card bp-stack">
            <h2>{t("Vos workspaces", "Your workspaces")}</h2>
            {rows(view, "workspaces").map((w) => (
              <div key={text(w, "id")} className="eco-inline">
                <strong>{text(w, "name")}</strong>
                <span>
                  {text(w, "plan")} · {text(w, "role")}
                </span>
                <button
                  disabled={busy}
                  className="bp-btn bp-btn--secondary"
                  onClick={async () => {
                    if (await run("POST", "/workspaces/select", { id: w.id }))
                      window.location.reload();
                  }}
                >
                  {t("Ouvrir", "Open")}
                </button>
              </div>
            ))}
            <form
              className="bp-stack"
              onSubmit={form((d) =>
                run("POST", "/workspaces", { name: d.get("name") }),
              )}
            >
              <Field
                name="name"
                label={t("Nouveau workspace", "New workspace")}
                maxLength={80}
                required
              />
              {btn(t("Créer un workspace", "Create a workspace"))}
            </form>
          </section>
          {invite && (
            <section className="bp-card bp-stack">
              <h2>{t("Rejoindre une équipe", "Join a team")}</h2>
              <p>
                {t(
                  "Utilisez le compte correspondant à l’adresse invitée. Le lien expire après sept jours.",
                  "Use the account matching the invited email address. The link expires after seven days.",
                )}
              </p>
              <button
                disabled={busy}
                className="bp-btn bp-btn--primary"
                onClick={async () => {
                  if (await run("POST", "/team/accept", { token: invite })) {
                    history.replaceState(null, "", location.pathname);
                    window.location.reload();
                  }
                }}
              >
                {t("Accepter l’invitation", "Accept invitation")}
              </button>
            </section>
          )}
          <section className="bp-stack">
            <h2>
              {t("Membres", "Members")} · {text(view, "seats")} /{" "}
              {text(view, "limit")}
            </h2>
            {rows(view, "members").map((m) => (
              <article key={text(m, "id")} className="bp-card bp-stack">
                <strong className="break">{text(m, "email")}</strong>
                <span>{text(m, "role")}</span>
                {admin && text(m, "role") !== "owner" && (
                  <form
                    className="eco-inline"
                    onSubmit={form((d) =>
                      run("PATCH", `/team/members/${m.id}`, {
                        role: d.get("role"),
                      }),
                    )}
                  >
                    <Field
                      name="role"
                      label={t("Rôle", "Role")}
                      defaultValue={text(m, "role")}
                    >
                      <option value="admin">Admin</option>
                      <option value="editor">Editor</option>
                      <option value="viewer">Viewer</option>
                    </Field>
                    {btn(t("Changer le rôle", "Change role"))}
                    <button
                      className="bp-btn bp-btn--secondary"
                      type="button"
                      disabled={busy}
                      onClick={() => {
                        if (
                          window.confirm(
                            t(
                              "Retirer ce membre du workspace ?",
                              "Remove this member from the workspace?",
                            ),
                          )
                        )
                          void run("DELETE", `/team/members/${m.id}`);
                      }}
                    >
                      {t("Retirer", "Remove")}
                    </button>
                  </form>
                )}
              </article>
            ))}
          </section>
          {admin && (
            <section className="bp-card bp-stack">
              <h2>{t("Inviter un membre", "Invite a member")}</h2>
              <p>
                {t(
                  "Owner : facturation et administration. Admin : membres et connexions. Editor : ressources. Viewer : lecture seule. Les invitations en attente réservent une place.",
                  "Owner: billing and administration. Admin: members and integrations. Editor: resources. Viewer: read only. Pending invitations reserve a seat.",
                )}
              </p>
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  run("POST", "/team/invitations", {
                    email: d.get("email"),
                    role: d.get("role"),
                  }),
                )}
              >
                <Field
                  name="email"
                  type="email"
                  label="E-mail"
                  maxLength={254}
                  required
                />
                <Field
                  name="role"
                  label={t("Rôle", "Role")}
                  defaultValue="editor"
                >
                  <option value="admin">Admin</option>
                  <option value="editor">Editor</option>
                  <option value="viewer">Viewer</option>
                </Field>
                {btn(t("Envoyer l’invitation", "Send invitation"))}
              </form>
              {rows(view, "invitations").map((i) => (
                <div key={text(i, "id")} className="eco-inline">
                  <span className="break">
                    {text(i, "email")} · {text(i, "role")}
                  </span>
                  <button
                    className="bp-btn bp-btn--secondary"
                    disabled={busy}
                    onClick={() =>
                      void run("DELETE", `/team/invitations/${i.id}`)
                    }
                  >
                    {t("Révoquer", "Revoke")}
                  </button>
                </div>
              ))}
            </section>
          )}
          {!!entitlements["features.workspace_audit"] && (
            <section className="bp-card bp-stack">
              <h2>{t("Journal du workspace", "Workspace audit")}</h2>
              <button
                className="bp-btn bp-btn--secondary"
                onClick={async () => {
                  const r = await send<Row>("GET", "/team/audit");
                  if (r.ok && r.data) setResult(rows(r.data, "items"));
                  else
                    setError(t("Journal indisponible.", "Audit unavailable."));
                }}
              >
                {t("Afficher les dernières actions", "Show recent actions")}
              </button>
              <Table
                items={result}
                columns={[
                  ["action", "Action"],
                  ["target", t("Cible", "Target")],
                  ["at", "Date"],
                ]}
              />
            </section>
          )}
        </>
      )}
      {section === "integrations" && (
        <>
          <section className="bp-card bp-stack">
            <h2>{t("Connecter vos outils", "Connect your tools")}</h2>
            <p>
              {t(
                "Stripe, Shopify et WooCommerce transmettent uniquement les ventes payées portant beep_consent=true. Ajoutez beep_cid et, si consenti, beep_visitor aux métadonnées de commande. Configurez l’URL de webhook affichée après connexion dans votre outil.",
                "Stripe, Shopify and WooCommerce send only paid sales carrying beep_consent=true. Add beep_cid and, if consented, beep_visitor to order metadata. Configure the webhook URL shown after connection in your tool.",
              )}
            </p>
            <p>
              {t(
                "Zapier et Make reçoivent les événements signés. Slack reçoit les notifications. GA4 reçoit les leads et ventes consentis. Pro : Zapier et Make ; Elite : tous les connecteurs.",
                "Zapier and Make receive signed events. Slack receives notifications. GA4 receives consented leads and sales. Pro: Zapier and Make; Elite: all connectors.",
              )}
            </p>
            {admin && (
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  run("POST", "/integrations", {
                    provider,
                    name: d.get("name"),
                    secret: d.get("secret"),
                    account: d.get("account"),
                    url: d.get("url"),
                  }),
                )}
              >
                <label className="bp-field">
                  <span className="bp-field__label">
                    {t("Outil", "Provider")}
                  </span>
                  <select
                    className="bp-input"
                    value={provider}
                    onChange={(e) => setProvider(e.target.value)}
                  >
                    {[
                      "stripe",
                      "shopify",
                      "woocommerce",
                      "zapier",
                      "make",
                      "slack",
                      "ga4",
                    ].map((p) => (
                      <option key={p}>{p}</option>
                    ))}
                  </select>
                </label>
                <Field
                  name="name"
                  label={t("Nom de la connexion", "Connection name")}
                  maxLength={80}
                  required
                />
                {["stripe", "shopify", "woocommerce", "ga4"].includes(
                  provider,
                ) && (
                  <Field
                    name="secret"
                    type="password"
                    label={
                      provider === "ga4"
                        ? "API secret"
                        : "Webhook signing secret"
                    }
                    maxLength={4096}
                    required
                  />
                )}
                {["shopify", "ga4"].includes(provider) && (
                  <Field
                    name="account"
                    label={
                      provider === "shopify"
                        ? "Store (*.myshopify.com)"
                        : "Measurement ID (G-…)"
                    }
                    required
                  />
                )}
                {["zapier", "make", "slack"].includes(provider) && (
                  <Field
                    name="url"
                    type="url"
                    label={t("URL du webhook HTTPS", "HTTPS webhook URL")}
                    required
                  />
                )}
                {btn(t("Connecter", "Connect"))}
              </form>
            )}
          </section>
          {rows(view, "items").map((i) => (
            <article className="bp-card bp-stack" key={text(i, "id")}>
              <h2>{text(i, "name")}</h2>
              <span>
                {text(i, "provider")} · {text(i, "status")}
              </span>
              {["stripe", "shopify", "woocommerce"].includes(
                text(i, "provider"),
              ) && (
                <code className="developer-code">
                  {origin}
                  {text(i, "webhookPath")}
                </code>
              )}
              {admin && text(i, "status") !== "disabled" && (
                <button
                  className="bp-btn bp-btn--secondary"
                  disabled={busy}
                  onClick={() => {
                    if (
                      window.confirm(
                        t(
                          "Déconnecter cet outil ?",
                          "Disconnect this provider?",
                        ),
                      )
                    )
                      void run("DELETE", `/integrations/${i.id}`);
                  }}
                >
                  {t("Déconnecter", "Disconnect")}
                </button>
              )}
            </article>
          ))}
          <section className="bp-stack">
            <h2>{t("Événements reçus", "Incoming events")}</h2>
            {rows(view, "events").length === 0 && (
              <p className="muted">
                {t("Aucun événement reçu.", "No events received yet.")}
              </p>
            )}
            {rows(view, "events").map((e) => (
              <article className="bp-card bp-stack" key={text(e, "id")}>
                <strong>
                  {text(e, "event")} · {text(e, "status")}
                </strong>
                <span>
                  {text(e, "attempts")} {t("tentatives", "attempts")} ·{" "}
                  {text(e, "last_error")}
                </span>
                {admin && text(e, "status") === "failed" && (
                  <button
                    disabled={busy}
                    className="bp-btn bp-btn--secondary"
                    onClick={() =>
                      void run(
                        "POST",
                        `/integrations/events/${e.id}/replay`,
                        {},
                      )
                    }
                  >
                    {t("Rejouer", "Replay")}
                  </button>
                )}
              </article>
            ))}
          </section>
          <button
            className="bp-btn bp-btn--secondary"
            onClick={() => void refresh()}
          >
            {t("Actualiser", "Refresh")}
          </button>
        </>
      )}
      {section === "organization" && (
        <>
          {writable && (
            <section className="bp-card bp-stack">
              <h2>{t("Dossiers et tags", "Folders and tags")}</h2>
              <form
                className="eco-inline"
                onSubmit={form((d) =>
                  run("POST", "/organization/labels", {
                    kind: d.get("kind"),
                    name: d.get("name"),
                  }),
                )}
              >
                <Field name="kind" label="Type">
                  <option value="folder">{t("Dossier", "Folder")}</option>
                  <option value="tag">Tag</option>
                </Field>
                <Field
                  name="name"
                  label={t("Nom", "Name")}
                  maxLength={80}
                  required
                />
                {btn(t("Ajouter", "Add"))}
              </form>
              {rows(view, "labels").map((l) => (
                <div className="eco-inline" key={text(l, "id")}>
                  <span>
                    {text(l, "kind")} · {text(l, "name")}
                  </span>
                  <button
                    disabled={busy}
                    className="bp-btn bp-btn--ghost"
                    onClick={() =>
                      void run("DELETE", `/organization/labels/${l.id}`)
                    }
                  >
                    {t("Supprimer", "Delete")}
                  </button>
                </div>
              ))}
            </section>
          )}
          <label className="bp-field">
            <span className="bp-field__label">
              {t("Filtrer les liens", "Filter links")}
            </span>
            <input
              className="bp-input"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </label>
          {rows(view, "links")
            .filter((l) =>
              (text(l, "title") + text(l, "slug") + text(l, "destination"))
                .toLowerCase()
                .includes(filter.toLowerCase()),
            )
            .map((l) => (
              <article className="bp-card bp-stack" key={text(l, "id")}>
                <h2>{text(l, "title") || text(l, "slug")}</h2>
                <p className="break">{text(l, "destination")}</p>
                {writable && (
                  <form
                    className="bp-stack"
                    onSubmit={form((d) =>
                      run("PUT", `/organization/links/${l.id}`, {
                        folder: d.get("folder") || null,
                        tags: d.getAll("tags"),
                      }),
                    )}
                  >
                    <Field
                      name="folder"
                      label={t("Dossier", "Folder")}
                      defaultValue={text(l, "folder_id")}
                    >
                      <option value="">—</option>
                      {rows(view, "labels")
                        .filter((x) => x.kind === "folder")
                        .map((x) => (
                          <option key={text(x, "id")} value={text(x, "id")}>
                            {text(x, "name")}
                          </option>
                        ))}
                    </Field>
                    <fieldset className="eco-tags">
                      <legend>Tags</legend>
                      {rows(view, "labels")
                        .filter((x) => x.kind === "tag")
                        .map((x) => (
                          <label key={text(x, "id")}>
                            <input
                              type="checkbox"
                              name="tags"
                              value={text(x, "id")}
                              defaultChecked={
                                Array.isArray(l.tags) && l.tags.includes(x.id)
                              }
                            />
                            {text(x, "name")}
                          </label>
                        ))}
                    </fieldset>
                    {btn(t("Enregistrer", "Save"))}
                  </form>
                )}
              </article>
            ))}
          <section className="bp-card bp-stack">
            <h2>CSV</h2>
            <p>
              {t(
                "En-tête : destination,slug,title. 500 lignes maximum. Chaque ligne a son résultat ; les quotas habituels s’appliquent. Un nouvel import crée de nouveaux liens.",
                "Header: destination,slug,title. Up to 500 rows. Each row has its own result; normal quotas apply. Importing again creates new links.",
              )}
            </p>
            <a
              className="bp-btn bp-btn--secondary"
              href="/v1/organization/export.csv"
            >
              {t("Exporter les liens", "Export links")}
            </a>
            {writable && (
              <form
                className="bp-stack"
                onSubmit={form(async (d) => {
                  const f = d.get("csv");
                  if (!(f instanceof File) || f.size > 262144) {
                    setError(
                      t(
                        "Sélectionnez un CSV de moins de 256 Ko.",
                        "Select a CSV smaller than 256 KB.",
                      ),
                    );
                    return false;
                  }
                  return run("POST", "/organization/import", {
                    csv: await f.text(),
                  });
                })}
              >
                <Field
                  name="csv"
                  type="file"
                  label={t("Fichier CSV", "CSV file")}
                  required
                />
                {btn(t("Importer", "Import"))}
              </form>
            )}
            <Table
              items={result}
              columns={[
                ["row", t("Ligne", "Row")],
                ["status", t("Résultat", "Result")],
                ["slug", "Slug"],
                ["code", t("Erreur", "Error")],
              ]}
            />
          </section>
        </>
      )}
      {section === "radar" && (
        <>
          {!!entitlements["features.insights"] && (
            <p role="status">
              {stream
                ? t("Flux en direct connecté.", "Live feed connected.")
                : t("Connexion au flux en direct…", "Connecting to live feed…")}
            </p>
          )}
          <section className="bp-stack">
            <h2>{t("Dernières alertes", "Recent alerts")}</h2>
            {rows(view, "alerts").length === 0 && (
              <p className="muted">
                {t("Aucune alerte pour le moment.", "No alerts yet.")}
              </p>
            )}
            <Table
              items={rows(view, "alerts")}
              columns={[
                ["kind", "Type"],
                ["message", "Message"],
                ["created_at", "Date"],
              ]}
            />
          </section>
          {admin && (
            <section className="bp-card bp-stack">
              <h2>{t("Nouvelle alerte", "New alert")}</h2>
              <p>
                {t(
                  "Fenêtre de 15 minutes, au plus une alerte par règle et fenêtre. La chute de conversion nécessite au moins 20 clics. E-mails envoyés au propriétaire ; Slack et webhook sur Elite.",
                  "15-minute window, at most one alert per rule per window. Conversion drops require at least 20 clicks. Emails go to the owner; Slack and webhooks are available on Elite.",
                )}
              </p>
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  run("POST", "/radar/rules", {
                    kind: d.get("kind"),
                    threshold: Number(d.get("threshold")),
                    channel,
                    destination: d.get("destination"),
                    link: d.get("link") || null,
                  }),
                )}
              >
                <Field name="kind" label="Type">
                  <option value="click_spike">
                    {t("Pic de clics", "Click spike")}
                  </option>
                  <option value="conversion_drop">
                    {t(
                      "Ventes / clics sous le seuil (%)",
                      "Sales / clicks below threshold (%)",
                    )}
                  </option>
                  <option value="broken_link">
                    {t("Destination indisponible", "Destination unavailable")}
                  </option>
                  <option value="domain_error">
                    {t("Domaine en erreur", "Domain error")}
                  </option>
                </Field>
                <Field
                  name="threshold"
                  type="number"
                  min={1}
                  max={100000000}
                  defaultValue="100"
                  label={t("Seuil", "Threshold")}
                  required
                />
                <Field name="link" label={t("Lien suivi", "Tracked link")}>
                  <option value="">
                    {t("Tout le workspace", "Whole workspace")}
                  </option>
                  {rows(view, "health").map((l) => (
                    <option key={text(l, "id")} value={text(l, "id")}>
                      {text(l, "slug")}
                    </option>
                  ))}
                </Field>
                <label className="bp-field">
                  <span className="bp-field__label">Canal</span>
                  <select
                    className="bp-input"
                    value={channel}
                    onChange={(e) => setChannel(e.target.value)}
                  >
                    <option value="email">E-mail</option>
                    <option value="slack">Slack</option>
                    <option value="webhook">Webhook</option>
                  </select>
                </label>
                {channel !== "email" && (
                  <Field
                    name="destination"
                    type="url"
                    label="URL HTTPS"
                    required
                  />
                )}
                {btn(t("Activer l’alerte", "Enable alert"))}
              </form>
            </section>
          )}
          {rows(view, "rules").map((r) => (
            <article className="bp-card eco-inline" key={text(r, "id")}>
              <span>
                {text(r, "kind")} · {text(r, "threshold")} ·{" "}
                {text(r, "channel")}
              </span>
              {admin && (
                <button
                  className="bp-btn bp-btn--secondary"
                  disabled={busy}
                  onClick={() => void run("DELETE", `/radar/rules/${r.id}`)}
                >
                  {t("Supprimer", "Delete")}
                </button>
              )}
            </article>
          ))}
          <section className="bp-stack">
            <h2>{t("Santé des destinations", "Destination health")}</h2>
            <p className="muted">
              {t(
                "Vérification toutes les six heures. 0 : connexion refusée ou indisponible ; les redirections HTTP ne sont pas suivies.",
                "Checked every six hours. 0: connection refused or unavailable; HTTP redirects are not followed.",
              )}
            </p>
            <Table
              items={rows(view, "health")}
              columns={[
                ["slug", "Lien"],
                ["status", "HTTP"],
                ["checked_at", t("Dernier contrôle", "Last checked")],
              ]}
            />
          </section>
          {admin && (
            <section className="bp-card bp-stack">
              <h2>{t("Rapports e-mail", "Email reports")}</h2>
              <p>
                {t(
                  "Synthèse des clics et ventes, envoyée au propriétaire en UTC.",
                  "Click and sales summary, sent to the owner in UTC.",
                )}
              </p>
              <Table
                items={rows(view, "reports")}
                columns={[
                  ["frequency", t("Fréquence", "Frequency")],
                  ["enabled", t("Activé", "Enabled")],
                  ["next_at", t("Prochain envoi", "Next delivery")],
                ]}
              />
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  run("PUT", "/radar/reports", {
                    frequency: d.get("frequency"),
                    enabled: d.get("enabled") === "on",
                  }),
                )}
              >
                <Field name="frequency" label={t("Fréquence", "Frequency")}>
                  <option value="daily">{t("Quotidien", "Daily")}</option>
                  <option value="weekly">{t("Hebdomadaire", "Weekly")}</option>
                </Field>
                <label className="eco-check">
                  <input type="checkbox" name="enabled" defaultChecked />
                  {t("Activer les rapports", "Enable reports")}
                </label>
                {btn(t("Enregistrer", "Save"))}
              </form>
            </section>
          )}
        </>
      )}
      {section === "insights" && (
        <>
          <p>
            {t(
              "90 derniers jours. Les visiteurs sont dédupliqués par journée UTC. Ces tendances sont agrégées, sans profil individuel.",
              "Last 90 days. Visitors are deduplicated per UTC day. These are aggregate trends, without individual profiles.",
            )}
          </p>
          <h2>{t("Audience quotidienne", "Daily audience")}</h2>
          <Table
            items={rows(view, "daily")}
            columns={[
              ["day", "Date"],
              ["clicks", t("Clics", "Clicks")],
              ["visitors", t("Visiteurs du jour", "Daily visitors")],
            ]}
          />
          <h2>{t("Segments d’audience", "Audience segments")}</h2>
          <Table
            items={rows(view, "segments")}
            columns={[
              ["dimension", "Type"],
              ["key", "Segment"],
              ["clicks", t("Clics", "Clicks")],
            ]}
          />
          <h2>{t("Campagnes", "Campaigns")}</h2>
          <Table
            items={rows(view, "campaigns")}
            columns={[
              ["source", "Source"],
              ["campaign", t("Campagne", "Campaign")],
              ["events", t("Événements", "Events")],
              ["sales", t("Ventes", "Sales")],
            ]}
          />
        </>
      )}
      {section === "affiliates" && (
        <>
          <section className="bp-card bp-stack">
            <h2>{t("Recommandez Beep", "Refer Beep")}</h2>
            <p>
              {t(
                "20 % des montants encaissés pendant les 12 premiers mois du compte recommandé. Validation après 30 jours ; remboursements exclus. Les paiements de test sont exclus. Versements manuels après vérification par la finance.",
                "20% of collected payments during the referred account’s first 12 months. Approved after 30 days; refunds excluded. Test payments are excluded. Manual payouts after finance review.",
              )}
            </p>
            {rows(view, "account").length ? (
              rows(view, "account").map((a) => (
                <div className="bp-stack" key={text(a, "code")}>
                  <strong>
                    {t("Votre code", "Your code")} : {text(a, "code")}
                  </strong>
                  <a
                    className="text-link break"
                    href={`${en ? "/en" : ""}/affiliates?ref=${a.code}`}
                  >
                    {origin}/affiliates?ref={text(a, "code")}
                  </a>
                  <p>
                    {text(view, "referrals")}{" "}
                    {t("comptes recommandés", "referred accounts")}
                  </p>
                </div>
              ))
            ) : (
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  run("POST", "/affiliates/join", {
                    terms: d.get("terms") === "on",
                  }),
                )}
              >
                <label className="eco-check">
                  <input name="terms" type="checkbox" required />
                  {t(
                    "J’accepte les conditions affichées ci-dessus et l’interdiction de l’auto-parrainage.",
                    "I accept the terms above and the prohibition on self-referrals.",
                  )}
                </label>
                {btn(t("Rejoindre le programme", "Join the program"))}
              </form>
            )}
          </section>
          <section className="bp-card bp-stack">
            <h2>{t("Vous avez été recommandé ?", "Were you referred?")}</h2>
            <p>
              {t(
                "Ajoutez le code avant votre premier paiement et dans les 30 jours suivant la création du workspace. Un seul parrain par workspace.",
                "Add the code before your first payment and within 30 days of workspace creation. One referrer per workspace.",
              )}
            </p>
            <form
              className="bp-stack"
              onSubmit={form((d) =>
                run("POST", "/affiliates/referral", { code: d.get("code") }),
              )}
            >
              <Field
                name="code"
                label="Code"
                defaultValue={invite}
                maxLength={12}
                required
              />
              {btn(t("Associer ce code", "Apply referral code"))}
            </form>
          </section>
          <Table
            items={rows(view, "commissions")}
            columns={[
              ["amount_xaf", "FCFA"],
              ["status", t("État", "Status")],
              ["available_at", t("Date de validation", "Approval date")],
              ["reference", t("Référence de versement", "Payout reference")],
            ]}
          />
        </>
      )}
      {section === "bio-submissions" && (
        <>
          <p>
            {t(
              "Réponses avec consentement, conservées 90 jours. Accès réservé aux administrateurs du workspace.",
              "Consented responses, kept for 90 days. Workspace administrators only.",
            )}
          </p>
          {rows(view, "items").length === 0 && (
            <p>{t("Aucune réponse pour le moment.", "No responses yet.")}</p>
          )}
          {rows(view, "items").map((s) => (
            <article className="bp-card bp-stack" key={text(s, "id")}>
              <strong className="break">{text(s, "email")}</strong>
              <p className="eco-prewrap">{text(s, "message")}</p>
              <time>{text(s, "consent_at")}</time>
              <button
                disabled={busy}
                className="bp-btn bp-btn--secondary"
                onClick={() => {
                  if (
                    window.confirm(
                      t(
                        "Effacer définitivement cette réponse ?",
                        "Permanently erase this response?",
                      ),
                    )
                  )
                    void run("DELETE", `/bio-submissions/${s.id}`);
                }}
              >
                {t("Effacer", "Erase")}
              </button>
            </article>
          ))}
        </>
      )}
    </div>
  );
}
