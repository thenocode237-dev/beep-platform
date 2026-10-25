package io.beep.core.app.trust;
import io.beep.core.domain.link.Link;
public final class LinkSafety {
  private LinkSafety() {}
  public static void requireSafe(TrustUseCase trust,Link link) {
    trust.requireSafe(link.destination().url());
    var r=link.rules();
    if(r==null) return;
    if(r.onelink()!=null) {if(r.onelink().ios()!=null) trust.requireSafe(r.onelink().ios());if(r.onelink().android()!=null) trust.requireSafe(r.onelink().android());if(r.onelink().desktop()!=null) trust.requireSafe(r.onelink().desktop());}
    r.routes().forEach(x -> trust.requireSafe(x.url()));
    r.variants().forEach(x -> trust.requireSafe(x.url()));
  }
}
