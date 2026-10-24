package io.beep.core.app.link;

import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import java.util.UUID;

/** Un lien d'un autre workspace est traité comme inexistant (aucune fuite d'information). */
public final class OwnedLinks {
  private OwnedLinks() {}

  public static Link require(LinkRepository links, Workspaces.Context ctx, UUID id) {
    return links.findById(id).filter(l -> l.workspaceId().equals(ctx.id())).orElseThrow(() -> new DomainException("not_found"));
  }
}
