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
    PostgresSlugRegistryAdapter.claim(dsl, l.registryKey(), "link", l.id());
    dsl.insertInto(LINKS)
        .set(L_ID, l.id())
        .set(L_DOMAIN, l.domainId())
        .set(L_PASSWORD, l.passwordHash())
        .set(L_WORKSPACE, l.workspaceId())
        .set(L_SLUG, l.slug().value())
        .set(L_DESTINATION, l.destination().url())
        .set(L_TITLE, l.title())
        .set(L_STATUS, l.status().db())
        .set(L_REASON, l.disabledReason())
        .set(L_CREATED, odt(l.createdAt()))
        .set(L_UPDATED, odt(l.updatedAt()))
        .execute();
    dsl.execute("UPDATE links SET rules = ?::jsonb WHERE id = ?", LinkRulesJson.write(l.rules()), l.id());
  }

  @Override
  public void update(Link l) {
    String oldSlug = dsl.select(L_SLUG).from(LINKS).where(L_ID.eq(l.id())).fetchOne(L_SLUG);
    boolean slugChanged = oldSlug != null && !oldSlug.equals(l.slug().value());
    String oldKey = oldSlug == null ? null : Link.registryKey(l.domainId(), oldSlug);
    if (slugChanged) PostgresSlugRegistryAdapter.claim(dsl, l.registryKey(), "link", l.id());
    dsl.update(LINKS)
        .set(L_SLUG, l.slug().value())
        .set(L_DESTINATION, l.destination().url())
        .set(L_TITLE, l.title())
        .set(L_STATUS, l.status().db())
        .set(L_REASON, l.disabledReason())
        .set(L_PASSWORD, l.passwordHash())
        .set(L_UPDATED, odt(l.updatedAt()))
        .where(L_ID.eq(l.id()))
        .execute();
    dsl.execute("UPDATE links SET rules = ?::jsonb WHERE id = ?", LinkRulesJson.write(l.rules()), l.id());
    if (slugChanged) PostgresSlugRegistryAdapter.release(dsl, oldKey, l.id());
  }

  @Override
  public void delete(UUID id) {
    var deleted = dsl.deleteFrom(LINKS).where(L_ID.eq(id)).returning(L_SLUG, L_DOMAIN).fetchOptional();
    deleted.ifPresent(r -> PostgresSlugRegistryAdapter.release(dsl, Link.registryKey(r.get(L_DOMAIN), r.get(L_SLUG)), id));
  }

  @Override
  public Optional<Link> findById(UUID id) {
    return dsl.selectFrom(LINKS).where(L_ID.eq(id)).fetchOptional(PostgresLinkAdapter::map).map(l -> withHosts(List.of(l)).get(0));
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
    if (rows.size() <= limit) return new Page(withHosts(rows), null);
    List<Link> items = withHosts(rows.subList(0, limit));
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
    return dsl.select(L_ID, L_WORKSPACE, L_DOMAIN, L_RULES, L_PASSWORD, L_SLUG, L_DESTINATION, L_TITLE, L_STATUS, L_REASON, L_CREATED, L_UPDATED, U_EMAIL).from(LINKS).join(WORKSPACES).on(W_ID.eq(L_WORKSPACE)).leftJoin(USERS).on(U_ID.eq(W_OWNER));
  }

  private static Condition after(String cursor) {
    if (cursor == null || cursor.isBlank()) return DSL.trueCondition();
    Sql.Cursor c = Sql.cursor(cursor);
    return DSL.row(L_CREATED, L_ID).lt(DSL.row(odt(c.at()), c.id()));
  }

  private static AdminRow adminRow(Record r) {
    return new AdminRow(map(r), r.get(U_EMAIL));
  }

  /** Complète le nom d'hôte du domaine personnalisé des liens (une seule requête pour la page). */
  private List<Link> withHosts(List<Link> links) {
    java.util.Set<UUID> ids = new java.util.HashSet<>();
    for (Link l : links) if (l.domainId() != null) ids.add(l.domainId());
    if (ids.isEmpty()) return links;
    java.util.Map<UUID, String> hosts = new java.util.HashMap<>();
    dsl.fetch("SELECT id, hostname FROM custom_domains WHERE id = ANY (?::uuid[])", (Object) ids.toArray(new UUID[0])).forEach(r -> hosts.put(r.get("id", UUID.class), r.get("hostname", String.class)));
    return links.stream().map(l -> l.domainId() == null ? l : new Link(l.id(), l.workspaceId(), l.slug(), l.destination(), l.title(), l.status(), l.disabledReason(), l.createdAt(),
        l.updatedAt(), l.domainId(), hosts.get(l.domainId()), l.rules(), l.passwordHash())).toList();
  }

  static Link map(Record r) {
    return new Link(r.get(L_ID), r.get(L_WORKSPACE), new Slug(r.get(L_SLUG)), new Destination(r.get(L_DESTINATION)), r.get(L_TITLE),
        LinkStatus.fromDb(r.get(L_STATUS)), r.get(L_REASON), instant(r.get(L_CREATED)), instant(r.get(L_UPDATED)), r.get(L_DOMAIN), null, LinkRulesJson.read(r.get(L_RULES) == null ? null : r.get(L_RULES).data()), r.get(L_PASSWORD));
  }
}
