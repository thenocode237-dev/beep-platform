package io.beep.core.app.redirect;

import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.link.Destination;

/** Scan d'un QR dynamique (`/q/{code}`) : suit le lien cible, compté comme scan pour le QR et pour le lien. */
public class ResolveQrUseCase {
  private final SlugResolverPort resolver;
  private final ClickRecorder recorder;

  public ResolveQrUseCase(SlugResolverPort resolver, ClickRecorder recorder) {
    this.resolver = resolver;
    this.recorder = recorder;
  }

  public Outcome execute(String code, Visit visit) {
    if (code == null || !code.matches("[0-9A-Za-z]{4,16}")) return Outcome.NOT_FOUND;
    return resolver.resolveQr(code).<Outcome>map(t -> {
      if (t.unavailable()) return Outcome.UNAVAILABLE;
      recorder.record(visit, new ClickRecorder.Target(t.workspaceId(), EventKind.SCAN, t.linkId(), t.qrId(), null, null));
      return new Outcome.Redirect(new Destination(t.destination()).withQuery(visit.query()));
    }).orElse(Outcome.NOT_FOUND);
  }
}
