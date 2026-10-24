package io.beep.core.app.auth;

import java.time.Duration;

public record AuthSettings(
    Duration sessionTtl,
    Duration verifyEmailTtl,
    Duration resetPasswordTtl,
    int loginMaxAttempts,
    Duration loginWindow,
    int signupMaxPerClient,
    Duration signupWindow,
    String totpIssuer) {

  public static AuthSettings defaults() {
    return new AuthSettings(Duration.ofDays(14), Duration.ofHours(24), Duration.ofHours(1), 5, Duration.ofMinutes(15), 10, Duration.ofHours(1), "Beep");
  }
}
