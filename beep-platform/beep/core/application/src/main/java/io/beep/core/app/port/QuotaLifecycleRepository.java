package io.beep.core.app.port;
import java.time.Instant;
import java.util.*;
public interface QuotaLifecycleRepository {
  record Notice(UUID workspaceId,String email,String locale,long links,Instant expiredAt) {}
  List<Notice> notices(Instant now);
  void notified(UUID workspaceId,Instant now);
  String apply(Instant now);
}
