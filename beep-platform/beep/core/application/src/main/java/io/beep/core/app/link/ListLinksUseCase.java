package io.beep.core.app.link;

import io.beep.core.app.common.Paging;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Liens du workspace, avec leurs clics des 30 derniers jours (lus dans les agrégats). */
public class ListLinksUseCase {
  public record Item(Link link, long clicks30d) {}

  public record Page(List<Item> items, String nextCursor) {}

  private final Workspaces workspaces;
  private final LinkRepository links;
  private final AnalyticsRepository analytics;
  private final Clock clock;

  public ListLinksUseCase(Workspaces workspaces, LinkRepository links, AnalyticsRepository analytics, Clock clock) {
    this.workspaces = workspaces;
    this.links = links;
    this.analytics = analytics;
    this.clock = clock;
  }

  public Page execute(User actor, String query, int limit, String cursor) {
    Workspaces.Context ctx = workspaces.context(actor);
    LinkRepository.Page page = links.page(ctx.id(), Paging.query(query), Paging.limit(limit), cursor);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    List<UUID> ids = page.items().stream().map(Link::id).toList();
    Map<UUID, Long> clicks = ids.isEmpty() ? Map.of() : analytics.totalsByResource(AnalyticsRepository.Resource.LINK, ids, today.minusDays(29), today);
    return new Page(page.items().stream().map(l -> new Item(l, clicks.getOrDefault(l.id(), 0L))).toList(), page.nextCursor());
  }

  public Link get(User actor, UUID id) {
    return OwnedLinks.require(links, workspaces.context(actor), id);
  }
}
