package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.BioPageRepository;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.bio.BioSeo;
import io.beep.core.domain.bio.BioTheme;
import io.beep.core.domain.link.Slug;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresBioPageAdapter implements BioPageRepository {
  private final DSLContext dsl;

  public PostgresBioPageAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(BioPage p) {
    PostgresSlugRegistryAdapter.claim(dsl, p.slug().value(), "bio", p.id());
    dsl.insertInto(BIO)
        .set(B_ID, p.id())
        .set(B_WORKSPACE, p.workspaceId())
        .set(B_SLUG, p.slug().value())
        .set(B_TITLE, p.title())
        .set(B_DESCRIPTION, p.description())
        .set(B_AVATAR, p.avatarUrl())
        .set(B_THEME, p.theme().db())
        .set(B_SEO, seo(p.seo()))
        .set(B_CREATED, odt(p.createdAt()))
        .set(B_UPDATED, odt(p.updatedAt()))
        .execute();
    if (!p.blocks().isEmpty()) replaceBlocks(p.id(), p.blocks(), p.updatedAt());
  }

  @Override
  public void update(BioPage p) {
    String oldSlug = dsl.select(B_SLUG).from(BIO).where(B_ID.eq(p.id())).fetchOne(B_SLUG);
    boolean slugChanged = oldSlug != null && !oldSlug.equals(p.slug().value());
    if (slugChanged) PostgresSlugRegistryAdapter.claim(dsl, p.slug().value(), "bio", p.id());
    dsl.update(BIO)
        .set(B_SLUG, p.slug().value())
        .set(B_TITLE, p.title())
        .set(B_DESCRIPTION, p.description())
        .set(B_AVATAR, p.avatarUrl())
        .set(B_THEME, p.theme().db())
        .set(B_SEO, seo(p.seo()))
        .set(B_UPDATED, odt(p.updatedAt()))
        .where(B_ID.eq(p.id()))
        .execute();
    if (slugChanged) PostgresSlugRegistryAdapter.release(dsl, oldSlug, p.id());
  }

  @Override
  public void replaceBlocks(UUID pageId, List<BioBlock> blocks, Instant at) {
    dsl.deleteFrom(BLOCKS).where(BB_PAGE.eq(pageId)).execute();
    if (!blocks.isEmpty()) {
      var insert = dsl.insertInto(BLOCKS, BB_PAGE, BB_ID, BB_POSITION, BB_TYPE, BB_CONFIG);
      for (int i = 0; i < blocks.size(); i++) {
        BioBlock b = blocks.get(i);
        insert = insert.values(pageId, b.id(), i, b.type().db(), Sql.json(b.config()));
      }
      insert.execute();
    }
    dsl.update(BIO).set(B_UPDATED, odt(at)).where(B_ID.eq(pageId)).execute();
  }

  @Override
  public void delete(UUID id) {
    String slug = dsl.deleteFrom(BIO).where(B_ID.eq(id)).returning(B_SLUG).fetchOptional(B_SLUG).orElse(null);
    if (slug != null) PostgresSlugRegistryAdapter.release(dsl, slug, id);
  }

  @Override
  public Optional<BioPage> findById(UUID id) {
    return dsl.selectFrom(BIO).where(B_ID.eq(id)).fetchOptional().map(r -> map(r, blocks(List.of(id)).getOrDefault(id, List.of())));
  }

  @Override
  public List<BioPage> listByWorkspace(UUID workspaceId) {
    var rows = dsl.selectFrom(BIO).where(B_WORKSPACE.eq(workspaceId)).orderBy(B_CREATED.asc()).fetch();
    Map<UUID, List<BioBlock>> blocks = blocks(rows.map(r -> r.get(B_ID)));
    return rows.map(r -> map(r, blocks.getOrDefault(r.get(B_ID), List.of())));
  }

  @Override
  public long count(UUID workspaceId) {
    return dsl.fetchCount(BIO, B_WORKSPACE.eq(workspaceId));
  }

  private Map<UUID, List<BioBlock>> blocks(List<UUID> pageIds) {
    Map<UUID, List<BioBlock>> out = new HashMap<>();
    if (pageIds.isEmpty()) return out;
    dsl.selectFrom(BLOCKS).where(BB_PAGE.in(pageIds)).orderBy(BB_PAGE, BB_POSITION).forEach(r -> out.computeIfAbsent(r.get(BB_PAGE), k -> new ArrayList<>())
        .add(new BioBlock(r.get(BB_ID), BioBlock.Type.fromDb(r.get(BB_TYPE)), Sql.strings(r.get(BB_CONFIG)))));
    return out;
  }

  private static org.jooq.JSONB seo(BioSeo s) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (s.title() != null) m.put("title", s.title());
    if (s.description() != null) m.put("description", s.description());
    if (s.imageUrl() != null) m.put("imageUrl", s.imageUrl());
    m.put("noindex", s.noindex());
    return Sql.json(m);
  }

  private static BioPage map(Record r, List<BioBlock> blocks) {
    Map<String, Object> s = Sql.map(r.get(B_SEO));
    BioSeo seo = new BioSeo((String) s.get("title"), (String) s.get("description"), (String) s.get("imageUrl"), Boolean.TRUE.equals(s.get("noindex")));
    return new BioPage(r.get(B_ID), r.get(B_WORKSPACE), new Slug(r.get(B_SLUG)), r.get(B_TITLE), r.get(B_DESCRIPTION), r.get(B_AVATAR),
        BioTheme.parse(r.get(B_THEME)), seo, blocks, instant(r.get(B_CREATED)), instant(r.get(B_UPDATED)));
  }
}
