"use client";
import {
  useEffect,
  useId,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { send } from "@/lib/client";
type Row = Record<string, unknown>;
const text = (r: Row, k: string) => String(r[k] ?? "");
const rows = (r: Row, k: string): Row[] =>
  Array.isArray(r[k]) ? (r[k] as Row[]) : [];
function Field({
  label,
  name,
  children,
  defaultValue,
  type = "text",
  required = true,
}: {
  label: string;
  name: string;
  children?: ReactNode;
  defaultValue?: string;
  type?: string;
  required?: boolean;
}) {
  const id = useId();
  return (
    <label className="bp-field" htmlFor={id}>
      <span className="bp-field__label">{label}</span>
      {children ? (
        <select
          id={id}
          className="bp-input"
          name={name}
          defaultValue={defaultValue}
        >
          {children}
        </select>
      ) : (
        <input
          id={id}
          className="bp-input"
          name={name}
          type={type}
          defaultValue={defaultValue}
          required={required}
        />
      )}
    </label>
  );
}
function Records({ items, fields }: { items: Row[]; fields: string[] }) {
  return (
    <div className="bp-stack">
      {items.map((r, i) => (
        <article className="bp-card bp-stack" key={text(r, "id") || i}>
          <dl className="eco-dl">
            {fields.map((k) => (
              <div key={k}>
                <dt>{k}</dt>
                <dd className="break">{text(r, k) || "—"}</dd>
              </div>
            ))}
          </dl>
        </article>
      ))}
    </div>
  );
}
export function EcosystemConsolePanel({
  section = "ecosystem",
}: {
  section?: "ecosystem" | "content" | "affiliates" | "staff";
}) {
  const [view, setView] = useState<Row>({}),
    [workspace, setWorkspace] = useState<Row>(),
    [ws, setWs] = useState(""),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [permissions, setPermissions] = useState<string[]>([]),
    [draft, setDraft] = useState<Row>(),
    [token, setToken] = useState(""),
    [expires, setExpires] = useState(""),
    [preview, setPreview] = useState<Row>();
  const can = (p: string) => permissions.includes(p),
    endpoint =
      section === "ecosystem" ? "/ecosystem/monitor" : `/ecosystem/${section}`;
  async function get(url: string) {
    try {
      const r = await fetch(`/api${url}`, { cache: "no-store" });
      if (!r.ok) {
        setError(
          r.status === 403
            ? "Votre rôle ne permet pas cette action."
            : "Service indisponible. Réessayez.",
        );
        return undefined;
      }
      return (await r.json()) as Row;
    } catch {
      setError("Connexion interrompue. Réessayez.");
      return undefined;
    }
  }
  async function load() {
    const r = await get(endpoint);
    if (r) setView(r);
  }
  async function inspect() {
    const r = await get(`/ecosystem/workspaces/${encodeURIComponent(ws)}`);
    if (r) setWorkspace(r);
  }
  useEffect(() => {
    let active = true;
    void get("/auth/me").then((r) => {
      if (active && r && Array.isArray(r.permissions))
        setPermissions(r.permissions as string[]);
    });
    void load();
    return () => {
      active = false;
    };
  }, [section]);
  useEffect(() => {
    if (!token || !expires) return;
    const timeout = setTimeout(
      () => {
        setToken("");
        setPreview(undefined);
        setNotice("La vue en lecture seule a expiré.");
      },
      Math.max(0, new Date(expires).getTime() - Date.now()),
    );
    return () => clearTimeout(timeout);
  }, [token, expires]);
  async function mutate(
    url: string,
    body: Row,
    method: "POST" | "PATCH" = "POST",
  ) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const r = await send<Row>(method, url, body);
      if (!r.ok) {
        setError(
          r.status === 403
            ? "Action non autorisée."
            : r.code === "conflict"
              ? "Une version plus récente existe. Actualisez avant de réessayer."
              : "Action refusée. Vérifiez le motif, le TOTP et les champs.",
        );
        return false;
      }
      if (r.data?.token) {
        setToken(String(r.data.token));
        setExpires(String(r.data.expiresAt));
        const v = await send<Row>("POST", "/ecosystem/impersonation/read", {
          token: r.data.token,
        });
        if (v.ok) setPreview(v.data);
      }
      setNotice("Action enregistrée.");
      await load();
      if (ws && workspace) await inspect();
      return true;
    } finally {
      setBusy(false);
    }
  }
  function form(fn: (d: FormData) => Promise<boolean>) {
    return async (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      const f = e.currentTarget;
      if (await fn(new FormData(f))) f.reset();
    };
  }
  const approval = (
    <>
      <Field name="reason" label="Motif" />
      <Field name="totp" label="Code TOTP" type="text" />
    </>
  );
  const button = (label: string) => (
    <button
      className="bp-btn bp-btn--primary"
      disabled={busy || !!token}
      type="submit"
    >
      {busy ? "Enregistrement…" : label}
    </button>
  );
  return (
    <div className="bp-stack bp-stack--lg">
      <header className="page-heading">
        <p className="eyebrow">Exploitation</p>
        <h1>
          {
            {
              ecosystem: "Équipes & écosystème",
              content: "Contenu éditorial",
              affiliates: "Affiliation",
              staff: "Accès staff",
            }[section]
          }
        </h1>
      </header>
      {error && (
        <div role="alert" className="bp-card">
          {error}
        </div>
      )}
      {notice && <p role="status">{notice}</p>}
      {section === "ecosystem" && (
        <>
          <section className="bp-card bp-stack">
            <h2>Activité</h2>
            <p>{text(view, "teams")} équipes avec membres invités.</p>
            <h3>Intégrations</h3>
            <Records
              items={rows(view, "integrations")}
              fields={["provider", "status", "count"]}
            />
            <h3>Événements reçus</h3>
            <Records
              items={rows(view, "events")}
              fields={["status", "count"]}
            />
            <h3>Notifications</h3>
            <Records items={rows(view, "tasks")} fields={["status", "count"]} />
          </section>
          <section className="bp-stack">
            <h2>Cohortes et rétention</h2>
            <p>
              Cohortes des douze derniers mois. Actifs : au moins un clic humain
              dans les 30 derniers jours. Rétention à 90 jours : activité entre
              J90 et J120. Seules les cohortes ayant atteint J120 ont un
              dénominateur complet.
            </p>
            <Records
              items={rows(view, "cohorts")}
              fields={[
                "cohort",
                "workspaces",
                "active30d",
                "retained90d",
                "mature90d",
              ]}
            />
          </section>
          {can("INTEGRATIONS_READ") && (
            <section className="bp-card bp-stack">
              <h2>Inspecter un workspace</h2>
              <form
                className="bp-stack"
                onSubmit={(e) => {
                  e.preventDefault();
                  void inspect();
                }}
              >
                <label className="bp-field">
                  <span className="bp-field__label">
                    Identifiant du workspace
                  </span>
                  <input
                    className="bp-input"
                    value={ws}
                    onChange={(e) => setWs(e.target.value)}
                    required
                  />
                </label>
                <button disabled={busy} className="bp-btn bp-btn--secondary">
                  Ouvrir la fiche
                </button>
              </form>
            </section>
          )}
          {workspace && (
            <section className="bp-stack">
              <h2>Fiche du workspace</h2>
              <Records
                items={rows((workspace.team ?? {}) as Row, "members")}
                fields={["email", "role"]}
              />
              <h3>Intégrations</h3>
              <Records
                items={rows(workspace, "integrations")}
                fields={[
                  "id",
                  "provider",
                  "name",
                  "status",
                  "last_error",
                  "last_at",
                ]}
              />
              <h3>Erreurs d’intégration</h3>
              <Records
                items={rows(workspace, "events")}
                fields={["id", "event", "status", "attempts", "last_error"]}
              />
              {can("INTEGRATIONS_WRITE") && (
                <form
                  className="bp-card bp-stack"
                  onSubmit={form((d) =>
                    mutate(
                      `/ecosystem/workspaces/${encodeURIComponent(ws)}/actions`,
                      {
                        action: d.get("action"),
                        id: d.get("id"),
                        reason: d.get("reason"),
                        totp: d.get("totp"),
                      },
                    ),
                  )}
                >
                  <h3>Intervenir</h3>
                  <Field name="action" label="Action">
                    <option value="integration.disable">
                      Déconnecter une intégration
                    </option>
                    <option value="integration.replay">
                      Rejouer un événement en échec
                    </option>
                  </Field>
                  <Field
                    name="id"
                    label="Identifiant de l’intégration ou de l’événement"
                  />
                  {approval}
                  {button("Exécuter")}
                </form>
              )}
              {can("IMPERSONATE_READ") && !token && (
                <form
                  className="bp-card bp-stack"
                  onSubmit={form((d) =>
                    mutate(
                      `/ecosystem/workspaces/${encodeURIComponent(ws)}/actions`,
                      {
                        action: "impersonate",
                        reason: d.get("reason"),
                        totp: d.get("totp"),
                      },
                    ),
                  )}
                >
                  <h3>Ouvrir en lecture seule</h3>
                  <p>
                    Vue des ressources pendant 10 minutes, attachée à votre
                    session staff. Aucune session client n’est créée.
                  </p>
                  {approval}
                  {button("Ouvrir la vue")}
                </form>
              )}
              {token && (
                <section
                  className="bp-card bp-stack"
                  role="region"
                  aria-label="Vue en lecture seule"
                >
                  <h3>
                    Lecture seule · expire à{" "}
                    {new Date(expires).toLocaleTimeString("fr")}
                  </h3>
                  <Records
                    items={rows((preview?.view ?? {}) as Row, "links")}
                    fields={["slug", "title", "destination"]}
                  />
                  <Records
                    items={rows((preview?.view ?? {}) as Row, "bioPages")}
                    fields={["slug", "title", "description"]}
                  />
                  <button
                    className="bp-btn bp-btn--secondary"
                    onClick={async () => {
                      await send("POST", "/ecosystem/impersonation/stop", {
                        token,
                      });
                      setToken("");
                      setPreview(undefined);
                    }}
                  >
                    Fermer la vue
                  </button>
                </section>
              )}
              {can("SUPPORT_NOTES") && (
                <section className="bp-card bp-stack">
                  <h3>Notes de support</h3>
                  <Records
                    items={rows(workspace, "notes")}
                    fields={["email", "body", "created_at"]}
                  />
                  <form
                    className="bp-stack"
                    onSubmit={form((d) =>
                      mutate(
                        `/ecosystem/workspaces/${encodeURIComponent(ws)}/actions`,
                        {
                          action: "note",
                          text: d.get("text"),
                          reason: d.get("reason"),
                          totp: d.get("totp"),
                        },
                      ),
                    )}
                  >
                    <Field name="text" label="Note interne" />
                    {approval}
                    {button("Ajouter la note")}
                  </form>
                </section>
              )}
              <h3>Historique du workspace</h3>
              <Records
                items={rows(workspace, "audit")}
                fields={["action", "target", "at"]}
              />
            </section>
          )}
        </>
      )}
      {section === "content" && (
        <>
          <p>
            Les brouillons restent dans le back-office. La publication pousse
            une copie versionnée vers le site. Le corps accepte des paragraphes
            et des titres « ## » ; le HTML reste du texte.
          </p>
          <form
            key={text(draft ?? {}, "id") || "new"}
            className="bp-card bp-stack"
            onSubmit={form((d) =>
              mutate("/ecosystem/content", {
                kind: d.get("kind"),
                slug: d.get("slug"),
                locale: d.get("locale"),
                title: d.get("title"),
                body: d.get("body"),
                version: draft?.version ?? 0,
              }),
            )}
          >
            <h2>{draft ? "Modifier le brouillon" : "Nouveau brouillon"}</h2>
            <Field
              name="kind"
              label="Rubrique"
              defaultValue={text(draft ?? {}, "kind") || "blog"}
            >
              <option value="blog">Blog</option>
              <option value="roadmap">Feuille de route</option>
              <option value="announcement">Annonce</option>
            </Field>
            <Field
              name="locale"
              label="Langue"
              defaultValue={text(draft ?? {}, "locale") || "fr"}
            >
              <option value="fr">Français</option><option value="es">Español</option><option value="pt">Português</option>
              <option value="en">English</option>
            </Field>
            <Field
              name="slug"
              label="Adresse (slug)"
              defaultValue={text(draft ?? {}, "slug")}
            />
            <Field
              name="title"
              label="Titre"
              defaultValue={text(draft ?? {}, "title")}
            />
            <label className="bp-field">
              <span className="bp-field__label">Corps du texte</span>
              <textarea
                className="bp-input bp-textarea"
                name="body"
                rows={12}
                maxLength={50000}
                defaultValue={text(draft ?? {}, "body")}
              />
            </label>
            {button("Enregistrer le brouillon")}
            <button
              type="button"
              className="bp-btn bp-btn--ghost"
              onClick={() => setDraft(undefined)}
            >
              Nouveau contenu
            </button>
          </form>
          {rows(view, "items").map((d) => (
            <article className="bp-card bp-stack" key={text(d, "id")}>
              <h2>{text(d, "title")}</h2>
              <p>
                {text(d, "kind")} / {text(d, "slug")} · {text(d, "locale")} · v
                {text(d, "version")} · {text(d, "status")}
              </p>
              <button
                className="bp-btn bp-btn--secondary"
                onClick={() => setDraft(d)}
              >
                Modifier
              </button>
              {can("CONTENT_PUBLISH") && (
                <form
                  className="bp-stack"
                  onSubmit={form((f) =>
                    mutate(`/ecosystem/content/${d.id}/publish`, {
                      status: f.get("status"),
                      reason: f.get("reason"),
                      totp: f.get("totp"),
                    }),
                  )}
                >
                  <Field name="status" label="Publication">
                    <option value="published">Publier</option>
                    <option value="withdrawn">Retirer du site</option>
                  </Field>
                  {approval}
                  {button("Appliquer la publication")}
                </form>
              )}
            </article>
          ))}
        </>
      )}
      {section === "affiliates" && (
        <>
          <p>
            Les commissions de test sont exclues. Seuls les montants approuvés
            après le délai de 30 jours peuvent être enregistrés comme versés.
            Effectuez le versement dans votre outil de paiement avant de le
            consigner ici.
          </p>
          <Records
            items={rows(view, "items")}
            fields={[
              "email",
              "code",
              "id",
              "amount_xaf",
              "status",
              "available_at",
              "reference",
            ]}
          />
          {can("AFFILIATES_PAY") && (
            <form
              className="bp-card bp-stack"
              onSubmit={form((d) =>
                mutate("/ecosystem/affiliates/payouts", {
                  commissions: d.getAll("commissions"),
                  reference: d.get("reference"),
                  reason: d.get("reason"),
                  totp: d.get("totp"),
                }),
              )}
            >
              <h2>Enregistrer un versement effectué</h2>
              {rows(view, "items")
                .filter((i) => i.status === "approved")
                .map((i) => (
                  <label className="eco-check" key={text(i, "id")}>
                    <input
                      type="checkbox"
                      name="commissions"
                      value={text(i, "id")}
                    />
                    {text(i, "email")} · {text(i, "amount_xaf")} FCFA
                  </label>
                ))}
              <Field name="reference" label="Référence du versement" />
              {approval}
              {button("Marquer les commissions versées")}
            </form>
          )}
        </>
      )}
      {section === "staff" && (
        <>
          <p>
            Une modification de rôle ou d’état révoque immédiatement les
            sessions du membre concerné. Votre propre compte ne peut pas être
            modifié ici.
          </p>
          {rows(view, "items").map((s) => (
            <article className="bp-card bp-stack" key={text(s, "id")}>
              <h2 className="break">{text(s, "email")}</h2>
              <form
                className="bp-stack"
                onSubmit={form((d) =>
                  mutate(
                    `/ecosystem/staff/${s.id}`,
                    {
                      role: d.get("role"),
                      active: d.get("active") === "on",
                      reason: d.get("reason"),
                      totp: d.get("totp"),
                    },
                    "PATCH",
                  ),
                )}
              >
                <Field name="role" label="Rôle" defaultValue={text(s, "role")}>
                  {[
                    "superadmin",
                    "support",
                    "finance",
                    "trust_safety",
                    "content",
                    "readonly",
                  ].map((r) => (
                    <option key={r}>{r}</option>
                  ))}
                </Field>
                <label className="eco-check">
                  <input
                    type="checkbox"
                    name="active"
                    defaultChecked={Boolean(s.active)}
                  />
                  Compte actif
                </label>
                {approval}
                {button("Mettre à jour l’accès")}
              </form>
            </article>
          ))}
        </>
      )}
      <button
        className="bp-btn bp-btn--secondary"
        disabled={busy}
        onClick={() => void load()}
      >
        Actualiser
      </button>
    </div>
  );
}
