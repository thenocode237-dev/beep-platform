package io.beep.core.domain.invitation;

import io.beep.core.domain.user.Email;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Invitation à la bêta privée, liée à une adresse e-mail. L'inscription (tous moyens confondus) la consomme. */
public record Invitation(UUID id, Email email, String invitedBy, Instant createdAt, Instant expiresAt, Instant usedAt) {
  public static final Duration VALIDITY = Duration.ofDays(30);

  public enum Status {
    PENDING,
    USED,
    EXPIRED;

    public String db() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static Invitation create(UUID id, Email email, String invitedBy, Instant now) {
    return new Invitation(id, email, invitedBy, now, now.plus(VALIDITY), null);
  }

  public boolean usable(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public Status status(Instant now) {
    if (usedAt != null) return Status.USED;
    return expiresAt.isAfter(now) ? Status.PENDING : Status.EXPIRED;
  }
}
