package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.instant;
import static io.beep.core.adapter.out.postgres.Schema.odt;

import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.customdomain.DnsStatus;
import io.beep.core.domain.customdomain.TlsStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresCustomDomainAdapter implements CustomDomainRepository {
  private static final String COLUMNS = "d.id, d.workspace_id, d.hostname, d.dns_status, d.tls_status, d.verify_token, d.verified_at, d.last_check_at, d.last_error, d.is_default, "
      + "d.root_redirect_url, d.not_found_url, d.blocked, d.blocked_reason, d.created_at";

  private final DSLContext dsl;

  public PostgresCustomDomainAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(CustomDomain d) {
    try {
      dsl.execute("INSERT INTO custom_domains (id, workspace_id, hostname, dns_status, tls_status, verify_token, verified_at, last_check_at, last_error, is_default, root_redirect_url, "
              + "not_found_url, blocked, blocked_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?::timestamptz)",
          d.id(), d.workspaceId(), d.hostname(), d.dnsStatus().db(), d.tlsStatus().db(), d.verifyToken(), odt(d.verifiedAt()), odt(d.lastCheckAt()), d.lastError(), d.isDefault(),
          d.rootRedirectUrl(), d.notFoundUrl(), d.blocked(), d.blockedReason(), odt(d.createdAt()));
    } catch (org.jooq.exception.DataAccessException e) {
      if (Sql.hasState(e, Sql.UNIQUE_VIOLATION)) throw new DuplicateHostnameException(e);
      throw e;
    }
  }

  @Override
  public Optional<CustomDomain> findById(UUID id) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM custom_domains d WHERE d.id = ?", id).map(PostgresCustomDomainAdapter::map);
  }

  @Override
  public Optional<CustomDomain> findByHostname(String hostname) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM custom_domains d WHERE d.hostname = ?", hostname).map(PostgresCustomDomainAdapter::map);
  }

  @Override
  public Optional<CustomDomain> lockById(UUID id) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM custom_domains d WHERE d.id = ? FOR UPDATE", id).map(PostgresCustomDomainAdapter::map);
  }

  @Override
  public void update(CustomDomain d) {
    dsl.execute("UPDATE custom_domains SET dns_status = ?, tls_status = ?, verified_at = ?::timestamptz, last_check_at = ?::timestamptz, last_error = ?, is_default = ?, "
            + "root_redirect_url = ?, not_found_url = ?, blocked = ?, blocked_reason = ? WHERE id = ?",
        d.dnsStatus().db(), d.tlsStatus().db(), odt(d.verifiedAt()), odt(d.lastCheckAt()), d.lastError(), d.isDefault(), d.rootRedirectUrl(), d.notFoundUrl(), d.blocked(),
        d.blockedReason(), d.id());
  }

  @Override
  public void delete(UUID id) {
    dsl.execute("DELETE FROM custom_domains WHERE id = ?", id);
  }

  @Override
  public List<CustomDomain> forWorkspace(UUID workspaceId) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM custom_domains d WHERE d.workspace_id = ? ORDER BY d.created_at, d.id", workspaceId).map(PostgresCustomDomainAdapter::map);
  }

  @Override
  public long count(UUID workspaceId) {
    return dsl.fetchOne("SELECT count(*) FROM custom_domains WHERE workspace_id = ?", workspaceId).get(0, Long.class);
  }

  @Override
  public long linkCount(UUID domainId) {
    return dsl.fetchOne("SELECT count(*) FROM links WHERE domain_id = ?", domainId).get(0, Long.class);
  }

  @Override
  public void setDefault(UUID workspaceId, UUID domainId) {
    dsl.execute("UPDATE custom_domains SET is_default = false WHERE workspace_id = ? AND is_default AND id IS DISTINCT FROM ?", workspaceId, domainId);
    if (domainId != null) dsl.execute("UPDATE custom_domains SET is_default = true WHERE id = ? AND workspace_id = ?", domainId, workspaceId);
  }

  @Override
  public Optional<CustomDomain> defaultOf(UUID workspaceId) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM custom_domains d WHERE d.workspace_id = ? AND d.is_default", workspaceId).map(PostgresCustomDomainAdapter::map);
  }

  @Override
  public Map<UUID, String> hostnames(Set<UUID> ids) {
    Map<UUID, String> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    dsl.fetch("SELECT id, hostname FROM custom_domains WHERE id = ANY (?::uuid[])", (Object) ids.toArray(new UUID[0])).forEach(r -> out.put(r.get("id", UUID.class), r.get("hostname", String.class)));
    return out;
  }

  @Override
  public AdminPage adminPage(String query, String status, int limit, String cursor) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE true");
    if (query != null) {
      where.append(" AND d.hostname ILIKE ? ESCAPE '\\'");
      args.add(Sql.contains(query));
    }
    if (status != null) {
      if (status.equals("blocked")) where.append(" AND d.blocked");
      else {
        where.append(" AND d.dns_status = ?");
        args.add(status);
      }
    }
    if (cursor != null && !cursor.isBlank()) {
      Sql.Cursor c = Sql.cursor(cursor);
      where.append(" AND (d.created_at, d.id) < (?::timestamptz, ?)");
      args.add(odt(c.at()));
      args.add(c.id());
    }
    args.add(limit + 1);
    List<AdminRow> rows = dsl.fetch("SELECT " + COLUMNS + ", u.email AS owner_email, (SELECT count(*) FROM links l WHERE l.domain_id = d.id) AS link_count FROM custom_domains d "
            + "JOIN workspaces w ON w.id = d.workspace_id JOIN users u ON u.id = w.owner_id " + where + " ORDER BY d.created_at DESC, d.id DESC LIMIT ?", args.toArray())
        .map(r -> new AdminRow(map(r), r.get("owner_email", String.class), r.get("link_count", Long.class)));
    if (rows.size() <= limit) return new AdminPage(rows, null);
    List<AdminRow> items = rows.subList(0, limit);
    CustomDomain last = items.get(limit - 1).domain();
    return new AdminPage(items, Sql.cursor(last.createdAt(), last.id()));
  }

  @Override
  public Optional<AdminRow> adminFind(UUID id) {
    return dsl.fetchOptional("SELECT " + COLUMNS + ", u.email AS owner_email, (SELECT count(*) FROM links l WHERE l.domain_id = d.id) AS link_count FROM custom_domains d "
            + "JOIN workspaces w ON w.id = d.workspace_id JOIN users u ON u.id = w.owner_id WHERE d.id = ?", id)
        .map(r -> new AdminRow(map(r), r.get("owner_email", String.class), r.get("link_count", Long.class)));
  }

  private static CustomDomain map(Record r) {
    return new CustomDomain(r.get("id", UUID.class), r.get("workspace_id", UUID.class), r.get("hostname", String.class), DnsStatus.parse(r.get("dns_status", String.class)),
        TlsStatus.parse(r.get("tls_status", String.class)), r.get("verify_token", String.class), instant(r.get("verified_at", OffsetDateTime.class)),
        instant(r.get("last_check_at", OffsetDateTime.class)), r.get("last_error", String.class), r.get("is_default", Boolean.class), r.get("root_redirect_url", String.class),
        r.get("not_found_url", String.class), r.get("blocked", Boolean.class), r.get("blocked_reason", String.class), instant(r.get("created_at", OffsetDateTime.class)));
  }
}
