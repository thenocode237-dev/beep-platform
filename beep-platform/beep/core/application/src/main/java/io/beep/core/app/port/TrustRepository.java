package io.beep.core.app.port;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustRepository {
  default void lockSafety() {}
  record Report(UUID id, String url, String category, String detail, String source, String status, String reason, Instant createdAt, Instant resolvedAt) {}
  record Reports(List<Report> items, long open, long overdue) {}
  record BlockedHost(String host, String reason, Instant createdAt) {}
  record Flag(String key, boolean enabled, int rolloutPercent, int version, String reason) {}
  record ScanTarget(UUID id, String url, Instant updatedAt, List<String> urls) {}
  Optional<String> destinationOf(String url,String mainHost);
  void report(Report report);
  Reports reports(String status, int limit, int offset, Instant now);
  Report resolve(UUID id, String decision, String reason, String staff, Instant now);
  List<BlockedHost> blocklist();
  void block(String host, boolean blocked, String reason, String staff, Instant now);
  boolean blocked(String host);
  List<Flag> flags();
  Flag publishFlag(String key, boolean enabled, int rollout, int version, String reason, String staff, Instant now);
  List<ScanTarget> scanTargets(int limit);
  boolean scanned(UUID id, String expectedUrl, Instant expectedUpdate, Instant now);
  void audit(String actor, String action, String target, String reason);
}
