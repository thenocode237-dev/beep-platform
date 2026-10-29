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
    String totpIssuer,
    Duration magicLinkTtl,
    Duration mfaTtl,
    boolean inviteOnly) {

  public static AuthSettings defaults() {
    return new AuthSettings(Duration.ofDays(14), Duration.ofHours(24), Duration.ofHours(1), 5, Duration.ofMinutes(15), 10, Duration.ofHours(1), "BeepClick",
        Duration.ofMinutes(15), Duration.ofMinutes(5), false);
  }

  /** Bêta privée : seules les adresses invitées par le staff peuvent créer un compte (tous moyens d'inscription). */
  public AuthSettings withInviteOnly(boolean value) {
    return new AuthSettings(sessionTtl, verifyEmailTtl, resetPasswordTtl, loginMaxAttempts, loginWindow, signupMaxPerClient, signupWindow, totpIssuer,
        magicLinkTtl, mfaTtl, value);
  }
}
