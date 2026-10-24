package io.beep.core.domain.link;

import io.beep.core.domain.DomainException;
import java.time.Instant;
import java.util.UUID;

/** Lien court. Immuable : chaque modification retourne une nouvelle instance. */
public record Link(
    UUID id,
    UUID workspaceId,
    Slug slug,
    Destination destination,
    String title,
    LinkStatus status,
    String disabledReason,
    Instant createdAt,
    Instant updatedAt) {
  public static final int MAX_TITLE = 200;
  public static final int MAX_REASON = 500;

  public static Link create(UUID id, UUID workspaceId, Slug slug, Destination destination, String title, Instant now) {
    return new Link(id, workspaceId, slug, destination, title(title), LinkStatus.ACTIVE, null, now, now);
  }

  public boolean disabled() {
    return status == LinkStatus.DISABLED;
  }

  public Link edit(Slug newSlug, Destination newDestination, String newTitle, Instant now) {
    return new Link(id, workspaceId, newSlug, newDestination, title(newTitle), status, disabledReason, createdAt, now);
  }

  public Link disable(String reason, Instant now) {
    return new Link(id, workspaceId, slug, destination, title, LinkStatus.DISABLED, reason(reason), createdAt, now);
  }

  public Link enable(Instant now) {
    return new Link(id, workspaceId, slug, destination, title, LinkStatus.ACTIVE, null, createdAt, now);
  }

  static String title(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String t = raw.strip();
    if (t.length() > MAX_TITLE) throw new DomainException("invalid_title");
    return t;
  }

  /** Motif d'une décision staff : obligatoire et borné (il est journalisé). */
  public static String reason(String raw) {
    if (raw == null || raw.strip().length() < 3 || raw.strip().length() > MAX_REASON) throw new DomainException("invalid_reason");
    return raw.strip();
  }
}
