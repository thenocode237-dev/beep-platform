"use client";
import { useState, type FormEvent, type InputHTMLAttributes } from "react";
import { send, errorMessage } from "@/lib/client";
type Row = Record<string, unknown>;
const localDateTime = (value: string) => {
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? ""
    : new Date(d.getTime() - d.getTimezoneOffset() * 60000)
        .toISOString()
        .slice(0, 16);
};
const text = (r: Row, k: string) => String(r[k] ?? "");
const rows = (r: Row, k: string) => (Array.isArray(r[k]) ? r[k] : []) as Row[];
function Field({
  label,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="bp-field">
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}
function Records({
  items,
  fields,
}: {
  items: Row[];
  fields: [string, string][];
}) {
  return (
    <div className="bp-stack">
      {items.length === 0 ? (
        <p>Aucun élément enregistré.</p>
      ) : (
        items.map((row, i) => (
          <article key={String(row.id ?? i)} className="bp-card bp-stack">
            <dl>
              {fields.map(([k, label]) => (
                <div key={k}>
                  <dt>{label}</dt>
                  <dd className="break">
                    {typeof row[k] === "object"
                      ? JSON.stringify(row[k])
                      : text(row, k)}
                  </dd>
                </div>
              ))}
            </dl>
          </article>
        ))
      )}
    </div>
  );
}
export function EnterpriseConsolePanel({
  initial,
  status,
  permissions,
}: {
  initial: Row;
  status: number;
  permissions: string[];
}) {
  const [view, setView] = useState(initial),
    [workspace, setWorkspace] = useState(""),
    [contract, setContract] = useState<Row>({}),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  const can = (p: string) => permissions.includes(p);
  const refresh = async () => {
    const r = await fetch("/api/enterprise/overview", { cache: "no-store" });
    if (r.ok) setView((await r.json()) as Row);
  };
  const run = async (method: "POST" | "PUT", route: string, body: unknown) => {
    setBusy(true);
    setMessage("");
    try {
      const r = await send<Row>(method, route, body);
      if (r.ok) {
        setMessage("Enregistré.");
        if (method === "PUT" && r.data?.contract)
          setContract(r.data.contract as Row);
        await refresh();
      } else
        setMessage(
          r.code === "conflict"
            ? "La version a changé ou l’action n’est plus applicable. Rechargez les données."
            : r.code === "sso_host_not_allowed"
              ? "L’hôte du fournisseur SSO doit être autorisé dans la configuration de déploiement."
              : errorMessage(r.code),
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
  const security = (
    <>
      <Field
        name="reason"
        label="Motif de l’action"
        required
        minLength={3}
        maxLength={1000}
      />
      <Field
        name="totp"
        label="Code de vérification staff"
        autoComplete="one-time-code"
        inputMode="numeric"
        pattern="[0-9]{6}"
        required
      />
      <button className="bp-btn bp-btn--primary" disabled={busy}>
        Enregistrer
      </button>
    </>
  );
  const sso = (contract.sso ?? {}) as Row;
  return (
    <div className="bp-stack">
      <header>
        <p className="eyebrow">Exploitation</p>
        <h1>Entreprise & industrialisation</h1>
        <p>Contrats sur mesure, capacité et preuves de conformité.</p>
      </header>
      {status !== 200 && (
        <p role="alert">
          {status === 403
            ? "Votre rôle ne permet pas cette consultation."
            : "Données indisponibles. Réessayez après rétablissement du service."}
        </p>
      )}
      <p role="status" aria-live="polite">
        {message}
      </p>
      <section className="bp-stack">
        <h2>Nœuds et capacité</h2>
        <Records
          items={rows(view, "nodes")}
          fields={[
            ["id", "Nœud"],
            ["role", "Rôle"],
            ["region", "Région"],
            ["healthy", "Heartbeat récent"],
            ["heartbeat_at", "Dernier signal"],
            ["capacity", "Capacité"],
          ]}
        />
        <h3>Base et réplication</h3>
        <Records
          items={rows(view, "database")}
          fields={[
            ["replica", "Base en lecture seule"],
            ["bytes", "Taille (octets)"],
            ["connections", "Connexions"],
            ["max_connections", "Limite de connexions"],
            ["replay_age_seconds", "Âge du dernier rejeu (s)"],
          ]}
        />
        <Records
          items={rows(view, "replication")}
          fields={[
            ["application_name", "Réplica"],
            ["state", "État"],
            ["sync_state", "Synchronisation"],
            ["replay_lag", "Retard de rejeu"],
          ]}
        />
      </section>
      <section className="bp-card bp-stack">
        <h2>Contrat du workspace</h2>
        <form
          className="bp-stack"
          onSubmit={form(async (d) => {
            setBusy(true);
            try {
              const id = String(d.get("workspace"));
              const r = await fetch(
                "/api/enterprise/workspaces/" + encodeURIComponent(id),
                { cache: "no-store" },
              );
              if (r.ok) {
                const b = (await r.json()) as Row;
                setWorkspace(id);
                setContract((b.contract ?? {}) as Row);
                setMessage("");
              } else setMessage("Workspace introuvable ou accès refusé.");
            } catch {
              setMessage("Connexion impossible.");
            } finally {
              setBusy(false);
            }
          })}
        >
          <Field name="workspace" label="Identifiant du workspace" required />
          <button className="bp-btn bp-btn--secondary" disabled={busy}>
            Charger le contrat
          </button>
        </form>
        {workspace && (
          <p className="break">
            Workspace : {workspace} · version {text(contract, "version") || "0"}
            . <a href="/plans">Plans et droits sur mesure</a> ·{" "}
            <a href="/billing">Facturation manuelle</a>
          </p>
        )}
        {workspace && can("ENTERPRISE_WRITE") && (
          <form
            key={workspace + text(contract, "version")}
            className="bp-stack"
            onSubmit={form(async (d) => {
              const b: Row = {
                version: Number(contract.version ?? 0) + 1,
                reference: d.get("reference"),
                status: d.get("status"),
                startsAt: new Date(String(d.get("startsAt"))).toISOString(),
                endsAt: new Date(String(d.get("endsAt"))).toISOString(),
                slaBp: Number(d.get("slaBp")),
                retentionDays: Number(d.get("retentionDays")),
                dpaReference: d.get("dpaReference"),
                dpaSha256: d.get("dpaSha256"),
                ipAllowlist: String(d.get("ipAllowlist") ?? "")
                  .split(/[\n,]+/)
                  .map((x) => x.trim())
                  .filter(Boolean),
                brandName: d.get("brandName"),
                brandColor: d.get("brandColor"),
                reason: d.get("reason"),
                totp: d.get("totp"),
                sso: d.get("ssoEnabled")
                  ? {
                      ...Object.fromEntries(
                        [
                          "issuer",
                          "clientId",
                          "authorizationUrl",
                          "tokenUrl",
                          "jwksUrl",
                        ].map((k) => [k, d.get(k)]),
                      ),
                      required: d.get("ssoRequired") === "on",
                    }
                  : {},
              };
              if (d.get("ssoSecret")) b.ssoSecret = d.get("ssoSecret");
              await run("PUT", "/enterprise/workspaces/" + workspace, b);
            })}
          >
            <Field
              name="reference"
              label="Référence du contrat"
              defaultValue={text(contract, "reference")}
              required
              maxLength={200}
            />
            <label className="bp-field">
              État
              <select
                name="status"
                defaultValue={text(contract, "status") || "active"}
              >
                <option value="active">Actif</option>
                <option value="suspended">Suspendu</option>
                <option value="expired">Expiré</option>
              </select>
            </label>
            <Field
              name="startsAt"
              type="datetime-local"
              label="Début (heure locale)"
              defaultValue={localDateTime(text(contract, "startsAt"))}
              required
            />
            <Field
              name="endsAt"
              type="datetime-local"
              label="Fin (heure locale)"
              defaultValue={localDateTime(text(contract, "endsAt"))}
              required
            />
            <Field
              name="slaBp"
              type="number"
              label="SLA contractuel (9990 = 99,90 %)"
              min={9000}
              max={10000}
              defaultValue={Number(contract.slaBp ?? 9990)}
              required
            />
            <Field
              name="retentionDays"
              type="number"
              label="Rétention des statistiques (jours)"
              min={1}
              max={3650}
              defaultValue={Number(contract.retentionDays ?? 365)}
              required
            />
            <Field
              name="dpaReference"
              label="Référence du DPA signé"
              defaultValue={text(contract, "dpaReference")}
              required
            />
            <Field
              name="dpaSha256"
              label="Empreinte SHA-256 du DPA signé"
              defaultValue={text(contract, "dpaSha256")}
              pattern="[a-f0-9]{64}"
              required
            />
            <label className="bp-field">
              Réseaux IP autorisés (un CIDR par ligne ; vide = tous)
              <textarea
                name="ipAllowlist"
                defaultValue={
                  Array.isArray(contract.ipAllowlist)
                    ? contract.ipAllowlist.join("\n")
                    : ""
                }
              />
            </label>
            <Field
              name="brandName"
              label="Nom affiché"
              defaultValue={text(contract, "brandName") || "Beepclick"}
              maxLength={80}
              required
            />
            <Field
              name="brandColor"
              type="color"
              label="Couleur de marque"
              defaultValue={text(contract, "brandColor") || "#153d35"}
              required
            />
            <fieldset className="bp-stack">
              <legend>Connexion unique OIDC</legend>
              <label>
                <input
                  type="checkbox"
                  name="ssoEnabled"
                  defaultChecked={Boolean(sso.issuer)}
                />{" "}
                Activer le fournisseur OIDC
              </label>
              <label>
                <input
                  type="checkbox"
                  name="ssoRequired"
                  defaultChecked={sso.required === true}
                />{" "}
                Exiger SSO pour accéder au workspace
              </label>
              {[
                "issuer",
                "clientId",
                "authorizationUrl",
                "tokenUrl",
                "jwksUrl",
              ].map((k) => (
                <Field key={k} name={k} label={k} defaultValue={text(sso, k)} />
              ))}
              <Field
                name="ssoSecret"
                label="Nouveau secret client (laisser vide pour conserver)"
                type="password"
                autoComplete="new-password"
              />
              <p>
                Le fournisseur doit être autorisé dans le déploiement. Les
                membres existants gardent leur rôle ; le TOTP personnel reste
                exigé.
              </p>
            </fieldset>
            {security}
          </form>
        )}
      </section>
      <section className="bp-stack">
        <h2>Contrats enregistrés</h2>
        <Records
          items={rows(view, "contracts")}
          fields={[
            ["workspace_id", "Workspace"],
            ["reference", "Référence"],
            ["status", "État"],
            ["ends_at", "Fin"],
            ["sla_bp", "SLA (points de base)"],
          ]}
        />
      </section>
      <section className="bp-card bp-stack">
        <h2>Preuves de conformité</h2>
        <p>Les références de preuve ne constituent pas une certification.</p>
        <Records
          items={rows(view, "evidence")}
          fields={[
            ["framework", "Référentiel"],
            ["control", "Contrôle"],
            ["url", "Référence"],
            ["sha256", "SHA-256"],
            ["expires_at", "Échéance"],
            ["current", "À jour"],
          ]}
        />
        {can("COMPLIANCE_WRITE") && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/evidence", Object.fromEntries(d)),
            )}
          >
            <label>
              Référentiel
              <select name="framework">
                <option value="soc2">SOC 2</option>
                <option value="iso27001">ISO 27001</option>
                <option value="security_audit">Audit de sécurité</option>
                <option value="pentest">Test d’intrusion</option>
              </select>
            </label>
            <Field name="control" label="Contrôle ou mission" required />
            <Field
              name="url"
              type="url"
              label="URL HTTPS de la preuve"
              required
            />
            <Field
              name="sha256"
              label="SHA-256"
              pattern="[a-f0-9]{64}"
              required
            />
            <Field
              name="expiresAt"
              label="Échéance ISO (ex. 2027-01-01T00:00:00Z)"
              required
            />
            {security}
          </form>
        )}
      </section>
      <section className="bp-card bp-stack">
        <h2>Revues d’accès trimestrielles</h2>
        <Records
          items={rows(view, "reviews")}
          fields={[
            ["workspace_id", "Workspace"],
            ["decision", "Décision"],
            ["reviewed_at", "Date"],
            ["due_at", "Prochaine revue"],
            ["snapshot", "Accès examinés"],
          ]}
        />
        <Records
          items={rows(view, "staffReviews")}
          fields={[
            ["decision", "Décision staff"],
            ["due_at", "Prochaine revue staff"],
            ["snapshot", "Accès staff examinés"],
          ]}
        />
        {can("COMPLIANCE_WRITE") && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/access-reviews", Object.fromEntries(d)),
            )}
          >
            <Field name="workspaceId" label="Workspace examiné" required />
            <label>
              Décision
              <select name="decision">
                <option value="approved">Approuvé</option>
                <option value="remediation_required">
                  Correction nécessaire
                </option>
              </select>
            </label>
            {security}
          </form>
        )}
        {can("STAFF_MANAGE") && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/staff-reviews", Object.fromEntries(d)),
            )}
          >
            <h3>Revue de tous les accès staff</h3>
            <label>
              Décision
              <select name="decision">
                <option value="approved">Approuvé</option>
                <option value="remediation_required">
                  Correction nécessaire
                </option>
              </select>
            </label>
            {security}
          </form>
        )}
      </section>
      <section className="bp-card bp-stack">
        <h2>Versions de SDK</h2>
        <Records
          items={rows(view, "sdk")}
          fields={[
            ["platform", "Plateforme"],
            ["version", "Version"],
            ["status", "État"],
            ["minimum_version", "Version minimale"],
            ["url", "Archive"],
            ["sha256", "SHA-256"],
          ]}
        />
        {can("SDK_WRITE") && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/sdk-releases", Object.fromEntries(d)),
            )}
          >
            <label>
              Plateforme
              <select name="platform">
                <option value="android">Android</option>
                <option value="ios">iOS</option>
                <option value="typescript">TypeScript</option>
              </select>
            </label>
            <Field name="version" label="Version (ex. 1.0.0)" required />
            <Field
              name="minimumVersion"
              label="Version minimale (ex. 1.0.0)"
              required
            />
            <Field
              name="url"
              type="url"
              label="URL HTTPS de l’archive"
              required
            />
            <Field
              name="sha256"
              label="SHA-256"
              pattern="[a-f0-9]{64}"
              required
            />
            <label>
              État
              <select name="status">
                <option value="active">Active</option>
                <option value="deprecated">Dépréciée</option>
                <option value="revoked">Révoquée</option>
              </select>
            </label>
            {security}
          </form>
        )}
      </section>
      <section className="bp-card bp-stack">
        <h2>Bascule assistée</h2>
        <p>
          Consignez la demande, appliquez le runbook de bascule, puis confirmez
          le résultat. Cette action ne promeut pas une base et ne modifie pas le
          DNS.
        </p>
        <Records
          items={rows(view, "failovers")}
          fields={[
            ["id", "Demande"],
            ["node_id", "Nœud"],
            ["status", "État"],
            ["reason", "Motif"],
          ]}
        />
        {can("OPERATIONS_WRITE") && (
          <form
            className="bp-stack"
            onSubmit={form((d) =>
              run("POST", "/enterprise/failovers", Object.fromEntries(d)),
            )}
          >
            <label>
              Action
              <select name="action">
                <option value="request">
                  Demander (nœud sans signal depuis 90 s)
                </option>
                <option value="confirm">Confirmer la bascule effectuée</option>
                <option value="cancel">Annuler</option>
              </select>
            </label>
            <Field name="nodeId" label="Nœud pour une nouvelle demande" />
            <Field
              name="id"
              label="Identifiant de demande pour confirmer / annuler"
            />
            {security}
          </form>
        )}
      </section>
    </div>
  );
}
