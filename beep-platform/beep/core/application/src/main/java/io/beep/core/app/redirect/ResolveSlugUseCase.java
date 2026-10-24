package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.app.port.SlugResolverPort.PublishedBio;
import io.beep.core.app.port.SlugResolverPort.Resolution;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Slug;
import java.util.Optional;

/**
 * Chemin critique `beep.example/{slug}` : résolution (cache mémoire), contrôles, événement asynchrone, réponse.
 * Aucun appel réseau externe ; sur un succès de cache, aucun accès base. Les quotas n'interviennent jamais ici.
 */
public class ResolveSlugUseCase {
  private final SlugResolverPort resolver;
  private final ClickRecorder recorder;

  public ResolveSlugUseCase(SlugResolverPort resolver, ClickRecorder recorder) {
    this.resolver = resolver;
    this.recorder = recorder;
  }

  public Outcome execute(String rawSlug, Visit visit) {
    if (!Slug.looksValid(rawSlug)) return Outcome.NOT_FOUND;
    Resolution r = resolver.resolve(rawSlug);
    return switch (r.kind()) {
      case NONE -> Outcome.NOT_FOUND;
      case LINK -> {
        if (r.unavailable()) yield Outcome.UNAVAILABLE;
        recorder.record(visit, new ClickRecorder.Target(r.workspaceId(), EventKind.CLICK, r.targetId(), null, null, null));
        yield new Outcome.Redirect(new Destination(r.destination()).withQuery(visit.query()));
      }
      case BIO -> {
        Optional<PublishedBio> bio = resolver.bioPage(r.targetId());
        if (bio.isEmpty()) yield Outcome.NOT_FOUND;
        if (bio.get().workspaceSuspended()) yield Outcome.UNAVAILABLE;
        recorder.record(visit, new ClickRecorder.Target(r.workspaceId(), EventKind.VIEW, null, null, r.targetId(), null));
        yield new Outcome.ShowBio(bio.get().page());
      }
    };
  }
}
