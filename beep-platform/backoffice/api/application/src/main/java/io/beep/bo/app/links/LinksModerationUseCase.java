package io.beep.bo.app.links;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Recherche de liens, désactivation / réactivation (avec motif), slugs réservés. Recherches et décisions journalisées. */
public class LinksModerationUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final Clock clock;

  public LinksModerationUseCase(CoreGatewayPort core, AuditPort audit, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.clock = clock;
  }

  public CoreGatewayPort.LinksPage search(StaffUser actor, String query, UUID workspaceId, int limit, String cursor, String ip) {
    actor.require(Permission.LINKS_READ);
    CoreGatewayPort.LinksPage page = core.listLinks(query, workspaceId, limit, cursor, actor.id());
    String detail = "count=" + page.items().size() + (query == null || query.isBlank() ? "" : " q=" + query.strip()) + (workspaceId == null ? "" : " workspace=" + workspaceId);
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "links.search", "links", detail, ip));
    return page;
  }

  public CoreGatewayPort.LinkView get(StaffUser actor, UUID id, String ip) {
    actor.require(Permission.LINKS_READ);
    CoreGatewayPort.LinkView link = core.getLink(id, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "links.view", "link:" + id, null, ip));
    return link;
  }

  public CoreGatewayPort.LinkView disable(StaffUser actor, UUID id, String reason, String ip) {
    actor.require(Permission.LINKS_MODERATE);
    if (reason == null || reason.strip().length() < 3) throw new DomainException("invalid_reason");
    CoreGatewayPort.LinkView link = core.disableLink(id, reason.strip(), actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "links.disable", "link:" + id,
        "slug=" + link.slug() + " destination=" + link.destination() + " motif=" + reason.strip(), ip));
    return link;
  }

  public CoreGatewayPort.LinkView enable(StaffUser actor, UUID id, String ip) {
    actor.require(Permission.LINKS_MODERATE);
    CoreGatewayPort.LinkView link = core.enableLink(id, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "links.enable", "link:" + id, "slug=" + link.slug(), ip));
    return link;
  }

  public List<String> reservedSlugs(StaffUser actor) {
    actor.require(Permission.LINKS_READ);
    return core.reservedSlugs(actor.id());
  }
}
