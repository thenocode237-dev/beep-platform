package io.beep.core.app.trust;
import io.beep.core.app.port.*;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Destination;
import java.net.IDN;
import java.time.*;
import java.util.*;

/** Moderation never calls a third party on the redirect path. */
public class TrustUseCase {
  private io.beep.core.app.common.PublicSite site;
  public TrustUseCase withSite(io.beep.core.app.common.PublicSite site) {this.site=site;return this;}
  private final TrustRepository store;
  private final TransactionPort tx;
  private final RateLimiterPort limiter;
  private final Clock clock;
  public TrustUseCase(TrustRepository store, TransactionPort tx, RateLimiterPort limiter, Clock clock) {
    this.store = store; this.tx = tx; this.limiter = limiter; this.clock = clock;
  }
  public void report(String url, String category, String detail, String ip) {
    if (!limiter.tryAcquire("report:" + ip, 5, Duration.ofHours(1))) throw new DomainException("rate_limited");
    String submitted = Destination.parse(url).url();
    String target = site == null ? submitted : store.destinationOf(submitted,site.host()).orElse(submitted);
    if (!Set.of("phishing", "malware", "spam", "other").contains(category == null ? "" : category)) throw new DomainException("invalid_request");
    String description = detail == null ? "" : detail.strip();
    if (description.length() > 2000) throw new DomainException("invalid_request");
    if (!target.equals(submitted)) description = "Reported: " + submitted + "\n" + description;
    store.report(new TrustRepository.Report(UUID.randomUUID(), target, category, description, "public", "open", null, clock.instant(), null));
  }
  public TrustRepository.Reports reports(String status, int limit, int offset) {
    if (status != null && !Set.of("open", "dismissed", "blocked").contains(status)) throw new DomainException("invalid_request");
    if (limit < 1 || limit > 100 || offset < 0) throw new DomainException("invalid_request");
    return store.reports(status, limit, offset, clock.instant());
  }
  public TrustRepository.Report resolve(UUID id, String decision, String reason, String staff) {
    if (!Set.of("dismissed", "blocked").contains(decision == null ? "" : decision)) throw new DomainException("invalid_request");
    requireReason(reason);
    return tx.run(() -> {
      var report = store.resolve(id, decision, reason.strip(), staff, clock.instant());
      if (decision.equals("blocked")) store.block(host(java.net.URI.create(report.url()).getHost()), true, reason.strip(), staff, clock.instant());
      store.audit(staff, "moderation." + decision, id.toString(), reason.strip());
      return report;
    });
  }
  public List<TrustRepository.BlockedHost> blocklist() { return store.blocklist(); }
  public void block(String raw, boolean blocked, String reason, String staff) {
    String host = host(raw); requireReason(reason);
    if(site != null && host.equals(site.host())) throw new DomainException("invalid_host");
    tx.run(() -> { store.block(host, blocked, reason.strip(), staff, clock.instant()); store.audit(staff, blocked ? "host.block" : "host.unblock", host, reason.strip()); });
  }
  public List<TrustRepository.Flag> flags() { return store.flags(); }
  public TrustRepository.Flag flag(String key, boolean enabled, int rollout, int version, String reason, String staff) {
    if (!Set.of("links.rules", "deeplinks.simulator").contains(key) || rollout < 0 || rollout > 100 || version < 1) throw new DomainException("invalid_request");
    requireReason(reason);
    return tx.run(() -> {
      var flag = store.publishFlag(key, enabled, rollout, version, reason.strip(), staff, clock.instant());
      store.audit(staff, "flag.publish", key, reason.strip()); return flag;
    });
  }
  public void requireFlag(String key, UUID workspace) {
    var flag = flags().stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    if (flag == null || !flag.enabled() || Math.floorMod(workspace.hashCode(), 100) >= flag.rolloutPercent()) throw new DomainException("feature_unavailable");
  }
  public void requireSafe(String url) {
    store.lockSafety();
    String host = java.net.URI.create(Destination.parse(url).url()).getHost();
    if (store.blocked(host.toLowerCase(Locale.ROOT))) throw new DomainException("destination_blocked");
  }
  public String scan(UrlSafetyPort safety) {
    int checked = 0, unsafe = 0;
    for (var target : store.scanTargets(50)) {
      String unsafeUrl=target.url();
      var verdict = UrlSafetyPort.Verdict.SAFE;
      for(String url : target.urls()) {
        var result=safety.check(url);
        if(result != UrlSafetyPort.Verdict.SAFE) {verdict=result;unsafeUrl=url;break;}
      }
      final var finalVerdict=verdict;
      final String detectedUrl=unsafeUrl;
      if (verdict == UrlSafetyPort.Verdict.UNCONFIGURED) return "unconfigured";
      if (verdict == UrlSafetyPort.Verdict.UNAVAILABLE) throw new IllegalStateException("url_safety_unavailable");
      boolean applied = tx.run(() -> {
        if (!store.scanned(target.id(), target.url(), target.updatedAt(), clock.instant())) return false;
        if (finalVerdict == UrlSafetyPort.Verdict.UNSAFE) {
          store.report(new TrustRepository.Report(UUID.randomUUID(), detectedUrl, "malware", "Threat detected by URL safety provider", "safe_browsing", "open", null, clock.instant(), null));
          store.block(host(java.net.URI.create(detectedUrl).getHost()), true, "URL safety provider", "worker", clock.instant());
          store.audit("worker", "safety.block", target.id().toString(), "URL safety provider");
        }
        return true;
      });
      if (applied) { checked++; if (verdict == UrlSafetyPort.Verdict.UNSAFE) unsafe++; }
    }
    return "checked=" + checked + " unsafe=" + unsafe;
  }
  public static void requireReason(String reason) {
    if (reason == null || reason.strip().length() < 3 || reason.length() > 1000) throw new DomainException("invalid_reason");
  }
  private static String host(String raw) {
    try {
      String host = IDN.toASCII(raw.strip().toLowerCase(Locale.ROOT));
      if (host.length() > 253 || !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z0-9-]+") || host.contains("..")) throw new IllegalArgumentException();
      return host;
    } catch (RuntimeException e) { throw new DomainException("invalid_host"); }
  }
}
