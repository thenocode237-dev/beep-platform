package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.DomainRouterPort;
import java.util.Locale;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;

/** Routage par nom d'hôte, sans cache (une requête indexée). Décoré par {@link CachingDomainRouterAdapter}. */
public class PostgresDomainRouterAdapter implements DomainRouterPort {
  private final DSLContext dsl;
  private final Set<String> mainHosts;

  /** `mainHosts` : noms qui désignent le domaine de Beepclick (site public, localhost, noms de service internes). */
  public PostgresDomainRouterAdapter(DSLContext dsl, Set<String> mainHosts) {
    this.dsl = dsl;
    this.mainHosts = Set.copyOf(mainHosts);
  }

  @Override
  public Route route(String host) {
    String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
    if (h.isEmpty() || mainHosts.contains(h)) return Route.MAIN;
    Record r = dsl.fetchOne("SELECT d.id, d.dns_status, d.blocked, d.root_redirect_url, d.not_found_url, w.status AS ws_status FROM custom_domains d "
        + "JOIN workspaces w ON w.id = d.workspace_id WHERE d.hostname = ?", h);
    if (r == null) return Route.UNKNOWN;
    boolean suspended = "suspended".equals(r.get("ws_status", String.class));
    boolean blocked = r.get("blocked", Boolean.class) || suspended;
    boolean verified = "verified".equals(r.get("dns_status", String.class));
    return new Route(Route.Kind.CUSTOM, r.get("id", java.util.UUID.class), verified && !blocked, blocked, r.get("root_redirect_url", String.class), r.get("not_found_url", String.class));
  }
}
