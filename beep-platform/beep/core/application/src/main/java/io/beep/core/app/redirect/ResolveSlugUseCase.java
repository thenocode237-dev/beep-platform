package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.app.port.SlugResolverPort.PublishedBio;
import io.beep.core.app.port.SlugResolverPort.Resolution;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.link.Slug;
import java.util.Optional;

/**
 * Chemin critique `beepclick.app/{slug}` : résolution (cache mémoire), contrôles, événement asynchrone, réponse.
 * Aucun appel réseau externe ; sur un succès de cache, aucun accès base. Les quotas n'interviennent jamais ici.
 */
public class ResolveSlugUseCase {
  private final SlugResolverPort resolver;
  private final ClickRecorder recorder;
  private final LinkRouter router;
  private final PasswordHasherPort hasher;
  private final RateLimiterPort limiter;

  public ResolveSlugUseCase(SlugResolverPort resolver, ClickRecorder recorder) {
    this(resolver, recorder, LinkRouter.plain(recorder), null, null);
  }

  public ResolveSlugUseCase(SlugResolverPort resolver, ClickRecorder recorder, LinkRouter router, PasswordHasherPort hasher, RateLimiterPort limiter) {
    this.resolver = resolver;
    this.recorder = recorder;
    this.router = router;
    this.hasher = hasher;
    this.limiter = limiter;
  }

  /** Validation du mot de passe d'un lien protégé (formulaire). 10 essais / 10 min par visiteur et lien ; erreur = même page, avec message. */
  public Outcome unlock(String rawSlug, java.util.UUID domainId, String password, Visit visit) {
    if (!Slug.looksValid(rawSlug) || hasher == null) return Outcome.NOT_FOUND;
    Resolution r = resolver.resolve(io.beep.core.domain.link.Link.registryKey(domainId, rawSlug));
    if (r.kind() != Resolution.Kind.LINK) return Outcome.NOT_FOUND;
    if (r.unavailable()) return Outcome.UNAVAILABLE;
    if (r.passwordHash() == null) return route(r, visit, false);
    if (limiter != null && !limiter.tryAcquire("unlock:" + visit.ip() + ":" + r.targetId(), 10, java.time.Duration.ofMinutes(10))) return new Outcome.PasswordRequired(true);
    if (password == null || password.isEmpty() || password.length() > 200 || !hasher.matches(password, r.passwordHash())) return new Outcome.PasswordRequired(true);
    return route(r, visit, true);
  }

  private Outcome route(Resolution r, Visit visit, boolean passwordChecked) {
    return router.route(new LinkRouter.Resolved(r.targetId(), r.destination(), r.rules(), r.passwordHash()), visit,
        new ClickRecorder.Target(r.workspaceId(), EventKind.CLICK, r.targetId(), null, null, null), passwordChecked);
  }

  public Outcome execute(String rawSlug, Visit visit) {
    return execute(rawSlug, null, visit);
  }

  /** `domainId` : domaine personnalisé de la requête (null = domaine de BeepClick). Les pages bio n'existent que sur le domaine de BeepClick. */
  public Outcome execute(String rawSlug, java.util.UUID domainId, Visit visit) {
    if (!Slug.looksValid(rawSlug)) return Outcome.NOT_FOUND;
    Resolution r = resolver.resolve(io.beep.core.domain.link.Link.registryKey(domainId, rawSlug));
    return switch (r.kind()) {
      case NONE -> Outcome.NOT_FOUND;
      case LINK -> {
        if (r.unavailable()) yield Outcome.UNAVAILABLE;
        yield route(r, visit, false);
      }
      case BIO -> {
        Optional<PublishedBio> bio = resolver.bioPage(r.targetId());
        if (bio.isEmpty()) yield Outcome.NOT_FOUND;
        if (bio.get().workspaceSuspended()) yield Outcome.UNAVAILABLE;
        recorder.record(visit, new ClickRecorder.Target(r.workspaceId(), EventKind.VIEW, null, null, r.targetId(), null));
        yield new Outcome.ShowBio(bio.get().page(),bio.get().showBadge());
      }
    };
  }
}
