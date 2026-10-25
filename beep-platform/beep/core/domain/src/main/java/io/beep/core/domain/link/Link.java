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
    Instant updatedAt,
    UUID domainId,
    String domainHost,
    LinkRules rules,
    String passwordHash) {
  public static final int MAX_TITLE = 200;
  public static final int MAX_REASON = 500;

  public static Link create(UUID id, UUID workspaceId, Slug slug, Destination destination, String title, Instant now) {
    return create(id, workspaceId, slug, destination, title, now, null, null);
  }

  /** `domainId` : domaine personnalisé du lien (null = domaine de Beep). `domainHost` n'est qu'un champ de lecture, renseigné par le dépôt. */
  public static Link create(UUID id, UUID workspaceId, Slug slug, Destination destination, String title, Instant now, UUID domainId, String domainHost) {
    return new Link(id, workspaceId, slug, destination, title(title), LinkStatus.ACTIVE, null, now, now, domainId, domainHost, LinkRules.NONE, null);
  }

  /** Clé du lien dans le registre des chemins publics : le slug seul sur le domaine de Beep, `domaine/slug` sur un domaine personnalisé. */
  public String registryKey() {
    return registryKey(domainId, slug.value());
  }

  public static String registryKey(UUID domainId, String slug) {
    return domainId == null ? slug : domainId + "/" + slug;
  }

  public boolean disabled() {
    return status == LinkStatus.DISABLED;
  }

  public Link edit(Slug newSlug, Destination newDestination, String newTitle, Instant now) {
    return new Link(id, workspaceId, newSlug, newDestination, title(newTitle), status, disabledReason, createdAt, now, domainId, domainHost, rules, passwordHash);
  }

  public Link disable(String reason, Instant now) {
    return new Link(id, workspaceId, slug, destination, title, LinkStatus.DISABLED, reason(reason), createdAt, now, domainId, domainHost, rules, passwordHash);
  }

  public Link enable(Instant now) {
    return new Link(id, workspaceId, slug, destination, title, LinkStatus.ACTIVE, null, createdAt, now, domainId, domainHost, rules, passwordHash);
  }

  /** Remplace les règles ; `passwordHash` null = aucun mot de passe. */
  public Link withRules(LinkRules newRules, String newPasswordHash, Instant now) {
    return new Link(id, workspaceId, slug, destination, title, status, disabledReason, createdAt, now, domainId, domainHost, newRules, newPasswordHash);
  }

  public boolean passwordProtected() {
    return passwordHash != null;
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
