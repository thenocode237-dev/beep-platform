package io.beep.core.app.port;
import io.beep.core.app.analytics.Visit;
import java.util.UUID;
/** Never blocks or fails a redirect. Implementations use a bounded asynchronous queue. */
public interface ConversionTrackerPort {
  String track(UUID workspace, UUID link, String destination, Visit visit);
}
