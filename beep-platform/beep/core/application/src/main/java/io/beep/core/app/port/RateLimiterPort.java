package io.beep.core.app.port;

import java.time.Duration;

public interface RateLimiterPort {
  /** Compte une tentative ; retourne false si la limite {@code max} par {@code window} est dépassée. */
  boolean tryAcquire(String key, int max, Duration window);

  void reset(String key);
}
