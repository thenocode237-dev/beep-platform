package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.bio.BioBlock;
import java.util.UUID;

/** Clic sur un bloc lien, bouton ou image d'une page bio (`/_b/c/{page}/{bloc}`) : mesuré, puis redirigé. */
public class BioBlockClickUseCase {
  private final SlugResolverPort resolver;
  private final ClickRecorder recorder;

  public BioBlockClickUseCase(SlugResolverPort resolver, ClickRecorder recorder) {
    this.resolver = resolver;
    this.recorder = recorder;
  }

  public Outcome execute(UUID pageId, String blockId, Visit visit) {
    return resolver.bioPage(pageId).<Outcome>map(bio -> {
      if (bio.workspaceSuspended()) return Outcome.UNAVAILABLE;
      return bio.page().block(blockId).map(block -> target(block)).<Outcome>map(url -> {
        recorder.record(visit, new ClickRecorder.Target(bio.page().workspaceId(), EventKind.BIO_CLICK, null, null, pageId, blockId));
        return new Outcome.Redirect(url);
      }).orElse(Outcome.NOT_FOUND);
    }).orElse(Outcome.NOT_FOUND);
  }

  private static String target(BioBlock block) {
    return switch (block.type()) {
      case LINK, BUTTON -> block.config().get("url");
      case IMAGE -> block.config().get("href");
      default -> null;
    };
  }
}
