package io.beep.bo.app.auth;

import java.time.Duration;

public record StaffSettings(Duration sessionTtl, Duration idleTimeout, int loginMaxAttempts, Duration loginWindow, String totpIssuer) {
  /** Session courte : 8 h au plus, 30 min d'inactivité. */
  public static StaffSettings defaults() {
    return new StaffSettings(Duration.ofHours(8), Duration.ofMinutes(30), 5, Duration.ofMinutes(15), "Beepclick Back office");
  }
}
