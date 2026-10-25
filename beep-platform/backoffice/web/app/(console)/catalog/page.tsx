import { Badge } from "@beep/ui";
import { CatalogEditor } from "@/components/CatalogEditor";
import { Unavailable } from "@/components/Unavailable";
import { getCatalog, getMe } from "@/lib/api";

export default async function Catalog({ searchParams }: { searchParams: Promise<{ edit?: string }> }) {
  const { edit } = await searchParams;
  const [res, me] = await Promise.all([getCatalog(), getMe()]);
  const canWrite = me.data?.permissions.includes("CATALOG_WRITE") ?? false;
  const current = edit && edit !== "new" ? (res.data?.items.find((a) => a.code === edit) ?? null) : null;
  return (
    <div className="bp-stack bp-stack--lg page">
      <div className="page-heading">
        <div>
          <h1>Catalogue d'applications.</h1>
          <p>
            Les applications que Beep sait ouvrir depuis un lien. Chaque publication crée une version et prend effet sans déploiement. Testez toujours sur des appareils réels
            avant d'afficher une application comme prise en charge.
          </p>
        </div>
        {canWrite && (
          <a className="bp-btn bp-btn--secondary" href="/catalog?edit=new">
            Nouvelle application
          </a>
        )}
      </div>
      {!res.data ? (
        <Unavailable status={res.status} what="le catalogue" />
      ) : (
        <ul className="history">
          {res.data.items.map((a) => (
            <li key={a.code} className="bp-card">
              <div className="link-row">
                <strong>{a.name}</strong>
                <span className="bp-row">
                  <Badge soon={!a.enabled}>{a.enabled ? "Actif" : "Désactivé"}</Badge>
                  <span className="muted small">v{a.version}</span>
                </span>
              </div>
              <span className="muted small break">{a.hosts.join(", ")}</span>
              <a className="text-link" href={`/catalog?edit=${a.code}`}>
                {canWrite ? "Modifier / tester" : "Tester"}
              </a>
            </li>
          ))}
        </ul>
      )}
      {edit && res.data && (
        <section className="bp-card bp-stack" aria-labelledby="ed">
          <h2 id="ed">{current ? current.name : "Nouvelle application"}</h2>
          <CatalogEditor key={edit} app={current} canWrite={canWrite} />
        </section>
      )}
    </div>
  );
}
