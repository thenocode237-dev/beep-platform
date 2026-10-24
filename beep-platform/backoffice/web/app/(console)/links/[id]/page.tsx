import { Badge, Icon } from "@beep/ui";
import { LinkModerationForm } from "@/components/ConsoleForms";
import { Unavailable } from "@/components/Unavailable";
import { getLink, getMe } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";

export default async function LinkDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [res, me] = await Promise.all([getLink(id), getMe()]);
  const back = (
    <a className="back-link" href="/links">
      <Icon name="arrow-left" size={17} /> Liens
    </a>
  );
  if (!res.data)
    return (
      <div className="bp-stack bp-stack--lg page">
        {back}
        <Unavailable status={res.status} what="ce lien" />
      </div>
    );
  const l = res.data;
  const disabled = l.status === "disabled";
  return (
    <div className="bp-stack bp-stack--lg page">
      {back}
      <div className="page-heading">
        <div>
          <h1 className="break">/{l.slug}</h1>
          <div className="bp-row heading-badges">
            <Badge soon={disabled}>{disabled ? "Désactivé" : "Actif"}</Badge>
          </div>
        </div>
      </div>
      <div className="detail-grid">
        <section className="bp-card bp-stack" aria-labelledby="info">
          <h2 id="info">Lien</h2>
          <dl className="kv">
            <dt>Destination</dt>
            <dd className="break">
              {/* jamais cliquable : la destination peut être malveillante */}
              <code>{l.destination}</code>
            </dd>
            <dt>Titre</dt>
            <dd>{l.title ?? "—"}</dd>
            <dt>Propriétaire</dt>
            <dd>{l.ownerEmail ?? "—"}</dd>
            <dt>Création</dt>
            <dd className="bp-tabular">{fmtDateTime(l.createdAt)}</dd>
            <dt>Dernière modification</dt>
            <dd className="bp-tabular">{fmtDateTime(l.updatedAt)}</dd>
            {disabled && (
              <>
                <dt>Motif de désactivation</dt>
                <dd>{l.disabledReason}</dd>
              </>
            )}
          </dl>
          <a className="text-link" href={`/links?workspaceId=${l.workspaceId}`}>
            Autres liens de ce workspace <Icon name="arrow-right" size={16} />
          </a>
        </section>
        {me.data?.permissions.includes("LINKS_MODERATE") && (
          <section className="bp-card bp-stack" aria-labelledby="moderation">
            <h2 id="moderation">Modération</h2>
            <LinkModerationForm linkId={l.id} disabled={disabled} />
          </section>
        )}
      </div>
    </div>
  );
}
