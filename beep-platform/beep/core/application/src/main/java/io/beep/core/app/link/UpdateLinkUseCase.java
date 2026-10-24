package io.beep.core.app.link;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.util.UUID;

/**
 * Modification partielle : un champ absent (null) est inchangé ; un titre vide l'efface.
 * Changer la destination ne change pas le lien partagé (ni les QR dynamiques qui le suivent).
 */
public class UpdateLinkUseCase {
  public record Command(String destination, String slug, String title) {}

  private final Workspaces workspaces;
  private final LinkRepository links;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;

  public UpdateLinkUseCase(Workspaces workspaces, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock) {
    this.workspaces = workspaces;
    this.links = links;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public Link execute(User actor, UUID id, Command cmd) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    Link link = OwnedLinks.require(links, ctx, id);
    Destination destination = link.destination();
    if (cmd.destination() != null) {
      destination = Destination.parse(cmd.destination());
      destination.requireNotHost(site.host());
    }
    Slug slug = cmd.slug() == null || cmd.slug().equals(link.slug().value()) ? link.slug() : Slug.custom(cmd.slug());
    Link updated = link.edit(slug, destination, cmd.title() == null ? link.title() : cmd.title(), clock.instant());
    try {
      tx.run(() -> links.update(updated));
    } catch (SlugRegistryPort.SlugTakenException e) {
      throw new DomainException("slug_taken");
    }
    return updated;
  }
}
