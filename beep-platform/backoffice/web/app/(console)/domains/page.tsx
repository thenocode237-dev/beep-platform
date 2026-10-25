import { Badge } from "@beep/ui";
import { DomainActions } from "@/components/DomainActions";
import { SearchForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getDomains, getMe } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

const dnsLabel = { pending: "À vérifier", verified: "Vérifié", failed: "DNS incorrect" } as const;
const tlsLabel = { pending: "Certificat en attente", active: "HTTPS actif" } as const;
const errorLabel: Record<string, string> = { txt_missing: "TXT introuvable", routing_missing: "Ne pointe pas vers Beep", dns_unreachable: "DNS injoignable" };

export default async function Domains({ searchParams }: { searchParams: Promise<{ q?: string; status?: string; cursor?: string }> }) {
  const { q, status, cursor } = await searchParams;
  const [res, me] = await Promise.all([getDomains({ q, status, cursor }), getMe()]);
  const canModerate = me.data?.permissions.includes("DOMAINS_MODERATE") ?? false;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Domaines personnalisés.</h1>
          <p>Tous les domaines des clients, avec l'état de leur DNS et de leur certificat. Bloquer un domaine arrête ses liens et l'émission de son certificat.</p>
        </div>
      </div>
      <SearchForm action="/domains" q={q} label="Rechercher un domaine">
        <div className="bp-field">
          <label className="bp-field__label" htmlFor="dom-status">
            État
          </label>
          <select id="dom-status" name="status" className="bp-input" defaultValue={status ?? ""}>
            <option value="">Tous</option>
            <option value="pending">À vérifier</option>
            <option value="verified">Vérifiés</option>
            <option value="failed">DNS incorrect</option>
            <option value="blocked">Bloqués</option>
          </select>
        </div>
      </SearchForm>
      {!res.data ? (
        <Unavailable status={res.status} what="les domaines" />
      ) : res.data.items.length === 0 ? (
        <p className="bp-card muted">Aucun domaine.</p>
      ) : (
        <ul className="history">
          {res.data.items.map((d) => (
            <li key={d.id} className="bp-card bp-stack">
              <div className="link-row">
                <strong className="break">{d.hostname}</strong>
                <span className="bp-row">
                  <Badge soon={d.dnsStatus !== "verified"}>{dnsLabel[d.dnsStatus]}</Badge>
                  <Badge soon={d.tlsStatus !== "active"}>{tlsLabel[d.tlsStatus]}</Badge>
                  {d.blocked && <Badge soon>Bloqué</Badge>}
                </span>
              </div>
              <span className="muted small bp-tabular">
                <a href={`/users?q=${encodeURIComponent(d.ownerEmail ?? "")}`}>{d.ownerEmail}</a> · {d.links} liens · créé {fmtDateTime(d.createdAt)}
                {d.verifiedAt ? ` · vérifié ${fmtDateTime(d.verifiedAt)}` : ""}
                {d.lastError ? ` · ${errorLabel[d.lastError] ?? d.lastError}` : ""}
                {d.blockedReason ? ` · motif : ${d.blockedReason}` : ""}
              </span>
              <DomainActions id={d.id} blocked={d.blocked} canModerate={canModerate} />
            </li>
          ))}
        </ul>
      )}
      {res.data?.nextCursor && (
        <a className="bp-btn bp-btn--secondary" href={`/domains?cursor=${encodeURIComponent(res.data.nextCursor)}${q ? `&q=${encodeURIComponent(q)}` : ""}${status ? `&status=${status}` : ""}`}>
          Plus anciens
        </a>
      )}
    </div>
  );
}
