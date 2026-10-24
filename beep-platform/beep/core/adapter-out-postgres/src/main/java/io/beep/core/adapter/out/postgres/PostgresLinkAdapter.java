package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.LinkRepository;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.LinkStatus;
import io.beep.core.domain.link.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

public class PostgresLinkAdapter implements LinkRepository {
  private final DSLContext dsl;

  public PostgresLinkAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Link l) {
    PostgresSlugRegistryAdapter.claim(dsl, l.slug().value(), "link", l.id());
    dsl.insertInto(LINKS)
        .set(L_ID, l.id())
        .set(L_WORKSPACE, l.workspaceId())
        .set(L_SLUG, l.slug().value())
        .set(L_DESTINATION, l.destination().url())
        .set(L_TITLE, l.title())
        .set(L_STATUS, l.status().db())
        .set(L_REASON, l.disabledReason())
        .set(L_CREATED, odt(l.createdAt()))
        .set(L_UPDATED, odt(l.updatedAt()))
        .execute();
  }

  @Override
  public void update(Link l) {
    String oldSlug = dsl.select(L_SLUG).from(LINKS).where(L_ID.eq(l.id())).fetchOne(L_SLUG);
    boolean slugChanged = oldSlug != null && !oldSlug.equals(l.slug().value());
    if (slugChanged) PostgresSlugRegistryAdapter.claim(dsl, l.slug().value(), "link", l.id());
    dsl.update(LINKS)
        .set(L_SLUG, l.slug().value())
        .set(L_DESTINATION, l.destination().url())
        .set(L_TITLE, l.title())
        .set(L_STATUS, l.status().db())
        .set(L_REASON, l.disabledReason())
        .set(L_UPDATED, odt(l.updatedAt()))
        .where(L_ID.eq(l.id()))
        .execute();
    if (slugChanged) PostgresSlugRegistryAdapter.release(dsl, oldSlug, l.id());
  }

  @Override
  public void delete(UUID id) {
    String slug = dsl.deleteFrom(LINKS).where(L_ID.eq(id)).returning(L_SLUG).fetchOptional(L_SLUG).orElse(null);
    if (slug != null) PostgresSlugRegistryAdapter.release(dsl, slug, id);
  }

  @Override
  public Optional<Link> findById(UUID id) {
    return dsl.selectFrom(LINKS).where(L_ID.eq(id)).fetchOptional(PostgresLinkAdapter::map);
  }

  @Override
  public Page page(UUID workspaceId, String query, int limit, String cursor) {
    Condition where = L_WORKSPACE.eq(workspaceId);
    if (query != null) {
      String p = Sql.contains(query);
      where = where.and(L_SLUG.likeIgnoreCase(p, '\\').or(L_DESTINATION.likeIgnoreCase(p, '\\')).or(L_TITLE.likeIgnoreCase(p, '\\')));
    }
    where = where.and(after(cursor));
    List<Link> rows = dsl.selectFrom(LINKS).where(where).orderBy(L_CREATED.desc(), L_ID.desc()).limit(limit + 1).fetch(PostgresLinkAdapter::map);
    if (rows.size() <= limit) return new Page(rows, null);
    List<Link> items = rows.subList(0, limit);
    Link last = items.get(limit - 1);
    return new Page(items, Sql.cursor(last.createdAt(), last.id()));
  }

  @Override
  public long count(UUID workspaceId) {
    return dsl.fetchCount(LINKS, L_WORKSPACE.eq(workspaceId));
  }

  @Override
  public long countCreatedSince(UUID workspaceId, Instant since) {
    return dsl.fetchCount(LINKS, L_WORKSPACE.eq(workspaceId).and(L_CREATED.ge(odt(since))));
  }

  @Override
  public AdminPage adminSearch(String query, UUID workspaceId, int limit, String cursor) {
    Condition where = DSL.trueCondition();
    if (workspaceId != null) where = where.and(L_WORKSPACE.eq(workspaceId));
    if (query != null) {
      String p = Sql.contains(query);
      where = where.and(L_SLUG.likeIgnoreCase(p, '\\').or(L_DESTINATION.likeIgnoreCase(p, '\\')).or(U_EMAIL.likeIgnoreCase(p, '\\')));
    }
    where = where.and(after(cursor));
    List<AdminRow> rows = adminSelect().where(where).orderBy(L_CREATED.desc(), L_ID.desc()).limit(limit + 1).fetch(PostgresLinkAdapter::adminRow);
    if (rows.size() <= limit) return new AdminPage(rows, null);
    List<AdminRow> items = rows.subList(0, limit);
    Link last = items.get(limit - 1).link();
    return new AdminPage(items, Sql.cursor(last.createdAt(), last.id()));
  }

  @Override
  public Optional<AdminRow> adminFind(UUID id) {
    return adminSelect().where(L_ID.eq(id)).fetchOptional(PostgresLinkAdapter::adminRow);
  }

  private org.jooq.SelectOnConditionStep<? extends Record> adminSelect() {
    return dsl.select(L_ID, L_WORKSPACE, L_SLUG, L_DESTINATION, L_TITLE, L_STATUS, L_REASON, L_CREATED, L_UPDATED, U_EMAIL).from(LINKS).join(WORKSPACES).on(W_ID.eq(L_WORKSPACE)).leftJoin(USERS).on(U_ID.eq(W_OWNER));
  }

  private static Condition after(String cursor) {
    if (cursor == null || cursor.isBlank()) return DSL.trueCondition();
    Sql.Cursor c = Sql.cursor(cursor);
    return DSL.row(L_CREATED, L_ID).lt(DSL.row(odt(c.at()), c.id()));
  }

  private static AdminRow adminRow(Record r) {
    return new AdminRow(map(r), r.get(U_EMAIL));
  }

  static Link map(Record r) {
    return new Link(r.get(L_ID), r.get(L_WORKSPACE), new Slug(r.get(L_SLUG)), new Destination(r.get(L_DESTINATION)), r.get(L_TITLE),
        LinkStatus.fromDb(r.get(L_STATUS)), r.get(L_REASON), instant(r.get(L_CREATED)), instant(r.get(L_UPDATED)));
  }
}
