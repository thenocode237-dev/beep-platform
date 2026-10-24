package io.beep.bo.app.port;

import java.time.Duration;

public interface RateLimiterPort {
  boolean tryAcquire(String key, int max, Duration window);

  void reset(String key);
}
