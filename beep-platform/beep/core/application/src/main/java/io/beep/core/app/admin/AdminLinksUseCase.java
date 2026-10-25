package io.beep.core.app.admin;

import io.beep.core.app.common.Paging;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.ReservedSlugs;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Liens côté staff : recherche tous workspaces, désactivation (modération) et réactivation, slugs réservés. */
public class AdminLinksUseCase {
  private io.beep.core.app.trust.TrustUseCase trust;
  public AdminLinksUseCase withTrust(io.beep.core.app.trust.TrustUseCase trust) {this.trust=trust;return this;}
  private final LinkRepository links;
  private final TransactionPort tx;
  private final Clock clock;

  public AdminLinksUseCase(LinkRepository links, TransactionPort tx, Clock clock) {
    this.links = links;
    this.tx = tx;
    this.clock = clock;
  }

  public LinkRepository.AdminPage search(String query, UUID workspaceId, int limit, String cursor) {
    return links.adminSearch(Paging.query(query), workspaceId, Paging.limit(limit), cursor);
  }

  public LinkRepository.AdminRow get(UUID id) {
    return links.adminFind(id).orElseThrow(() -> new DomainException("not_found"));
  }

  /** Effet immédiat sur toutes les instances : la modification déclenche l'invalidation du cache (LISTEN/NOTIFY). */
  public LinkRepository.AdminRow disable(UUID id, String reason) {
    LinkRepository.AdminRow row = get(id);
    Link disabled = row.link().disable(reason, clock.instant());
    tx.run(() -> links.update(disabled));
    return new LinkRepository.AdminRow(disabled, row.ownerEmail());
  }

  public LinkRepository.AdminRow enable(UUID id) {
    LinkRepository.AdminRow row = get(id);
    Link enabled = row.link().enable(clock.instant());
    tx.run(() -> {if(trust!=null)io.beep.core.app.trust.LinkSafety.requireSafe(trust,enabled);links.update(enabled);});
    return new LinkRepository.AdminRow(enabled, row.ownerEmail());
  }

  public List<String> reservedSlugs() {
    return List.copyOf(ReservedSlugs.all());
  }
}
