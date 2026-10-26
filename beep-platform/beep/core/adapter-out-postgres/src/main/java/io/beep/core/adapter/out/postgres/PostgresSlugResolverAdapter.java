package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.SlugResolverPort;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/** Résolution non mise en cache : une requête indexée (clé primaire de `slugs`). Décorée par {@link CachingSlugResolverAdapter}. */
public class PostgresSlugResolverAdapter implements SlugResolverPort {
  private final DSLContext dsl;
  private final BioPageRepository bioPages;

  public PostgresSlugResolverAdapter(DSLContext dsl, BioPageRepository bioPages) {
    this.dsl = dsl;
    this.bioPages = bioPages;
  }

  @Override
  public Resolution resolve(String slug) {
    Record r = dsl.fetchOne("""
        SELECT s.kind, s.target_id, COALESCE(l.workspace_id, b.workspace_id), l.destination, l.status, w.status, l.rules::text, l.password_hash
        FROM slugs s
        LEFT JOIN links l ON s.kind = 'link' AND l.id = s.target_id
        LEFT JOIN bio_pages b ON s.kind = 'bio' AND b.id = s.target_id
        LEFT JOIN workspaces w ON w.id = COALESCE(l.workspace_id, b.workspace_id)
        WHERE s.slug = ?""", slug);
    if (r == null || r.get(2) == null) return Resolution.NONE;
    Resolution.Kind kind = "link".equals(r.get(0, String.class)) ? Resolution.Kind.LINK : Resolution.Kind.BIO;
    return new Resolution(kind, r.get(1, UUID.class), r.get(2, UUID.class), r.get(3, String.class), "disabled".equals(r.get(4, String.class)),
        "suspended".equals(r.get(5, String.class)), LinkRulesJson.read(r.get(6, String.class)), r.get(7, String.class));
  }

  @Override
  public Optional<QrTarget> resolveQr(String code) {
    Record r = dsl.fetchOne("""
        SELECT q.id, q.workspace_id, l.id, l.destination, l.status, w.status, l.rules::text, l.password_hash
        FROM qr_codes q JOIN links l ON l.id = q.link_id JOIN workspaces w ON w.id = q.workspace_id
        WHERE q.code = ? AND q.kind = 'dynamic'""", code);
    if (r == null) return Optional.empty();
    return Optional.of(new QrTarget(r.get(0, UUID.class), r.get(1, UUID.class), r.get(2, UUID.class), r.get(3, String.class),
        "disabled".equals(r.get(4, String.class)), "suspended".equals(r.get(5, String.class)), LinkRulesJson.read(r.get(6, String.class)), r.get(7, String.class)));
  }

  @Override
  public Optional<PublishedBio> bioPage(UUID pageId) {
    return bioPages.findById(pageId).map(p -> {
      var r=dsl.fetchSingle("SELECT beep_effective_right(w.id,'features.bio_advanced') AS advanced,w.status,beep_effective_right(w.id,'features.bio_badge_removable') AS removable FROM workspaces w JOIN plans p ON p.code=w.plan_code LEFT JOIN entitlement_overrides o ON o.workspace_id=w.id AND o.key='features.bio_badge_removable' WHERE w.id=?",p.workspaceId());
      var published=r.get("advanced",Long.class)==1L?p:p.withBlocks(p.blocks().stream().filter(b->!java.util.Set.of(io.beep.core.domain.bio.BioBlock.Type.VIDEO,io.beep.core.domain.bio.BioBlock.Type.AUDIO,io.beep.core.domain.bio.BioBlock.Type.EMAIL,io.beep.core.domain.bio.BioBlock.Type.FORM).contains(b.type())).toList(),p.updatedAt());
      return new PublishedBio(published,"suspended".equals(r.get("status",String.class)),r.get("removable",Long.class)!=1L);
    });
  }
}
