package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.link.Destination;

/** Scan d'un QR dynamique (`/q/{code}`) : suit le lien cible, compté comme scan pour le QR et pour le lien. */
public class ResolveQrUseCase {
  private final SlugResolverPort resolver;
  private final ClickRecorder recorder;
  private final LinkRouter router;
  private final PasswordHasherPort hasher;
  private final RateLimiterPort limiter;

  public ResolveQrUseCase(SlugResolverPort resolver, ClickRecorder recorder) {
    this(resolver, recorder, LinkRouter.plain(recorder), null, null);
  }

  public ResolveQrUseCase(SlugResolverPort resolver, ClickRecorder recorder, LinkRouter router, PasswordHasherPort hasher, RateLimiterPort limiter) {
    this.resolver = resolver;
    this.recorder = recorder;
    this.router = router;
    this.hasher = hasher;
    this.limiter = limiter;
  }

  /** Mot de passe saisi sur la page d'un QR dont le lien est protégé (mêmes limites que pour un lien). */
  public Outcome unlock(String code, String password, Visit visit) {
    if (code == null || !code.matches("[0-9A-Za-z]{4,16}") || hasher == null) return Outcome.NOT_FOUND;
    return resolver.resolveQr(code).<Outcome>map(t -> {
      if (t.unavailable()) return Outcome.UNAVAILABLE;
      if (t.passwordHash() != null) {
        if (limiter != null && !limiter.tryAcquire("unlock:" + visit.ip() + ":" + t.linkId(), 10, java.time.Duration.ofMinutes(10))) return new Outcome.PasswordRequired(true);
        if (password == null || password.isEmpty() || password.length() > 200 || !hasher.matches(password, t.passwordHash())) return new Outcome.PasswordRequired(true);
      }
      return router.route(new LinkRouter.Resolved(t.linkId(), t.destination(), t.rules(), t.passwordHash()), visit,
          new ClickRecorder.Target(t.workspaceId(), EventKind.SCAN, t.linkId(), t.qrId(), null, null), true);
    }).orElse(Outcome.NOT_FOUND);
  }

  public Outcome execute(String code, Visit visit) {
    if (code == null || !code.matches("[0-9A-Za-z]{4,16}")) return Outcome.NOT_FOUND;
    return resolver.resolveQr(code).<Outcome>map(t -> {
      if (t.unavailable()) return Outcome.UNAVAILABLE;
      // Un QR dynamique suit son lien, règles comprises ; un lien protégé par mot de passe se déverrouille depuis /{slug}.
      return router.route(new LinkRouter.Resolved(t.linkId(), t.destination(), t.rules(), t.passwordHash()), visit,
          new ClickRecorder.Target(t.workspaceId(), EventKind.SCAN, t.linkId(), t.qrId(), null, null), false);
    }).orElse(Outcome.NOT_FOUND);
  }
}
