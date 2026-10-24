package io.beep.core.app.link;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.link.SlugGenerator;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Création « en un champ » : destination obligatoire, slug personnalisé et titre optionnels.
 * Le contrôle de quota et l'insertion se font sous verrou du workspace : deux créations simultanées ne dépassent jamais le quota.
 */
public class CreateLinkUseCase {
  public record Command(String destination, String slug, String title) {}

  private static final int GENERATION_ATTEMPTS = 5;

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final LinkRepository links;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;

  public CreateLinkUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.links = links;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public Link execute(User actor, Command cmd) {
    Destination destination = Destination.parse(cmd.destination());
    destination.requireNotHost(site.host());
    Slug custom = cmd.slug() == null || cmd.slug().isBlank() ? null : Slug.custom(cmd.slug());
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    Instant now = clock.instant();

    for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
      Link link = Link.create(UUID.randomUUID(), ctx.id(), custom != null ? custom : SlugGenerator.next(), destination, cmd.title(), now);
      try {
        tx.run(() -> {
          workspaceRepository.lock(ctx.id());
          QuotaPolicy.requireCreatable(ctx.entitlements(), Resource.LINKS, workspaces.usage(ctx.id(), Resource.LINKS));
          links.insert(link);
          workspaceRepository.markFirstLink(ctx.id(), now);
        });
        return link;
      } catch (SlugRegistryPort.SlugTakenException taken) {
        if (custom != null) throw new DomainException("slug_taken");
        // slug généré déjà pris (improbable) : nouvel essai avec un autre slug
      }
    }
    throw new IllegalStateException("aucun slug libre après " + GENERATION_ATTEMPTS + " essais");
  }
}
