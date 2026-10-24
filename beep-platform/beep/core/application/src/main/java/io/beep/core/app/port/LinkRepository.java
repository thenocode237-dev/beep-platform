package io.beep.core.app.port;

import io.beep.core.domain.link.Link;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {
  /** Lève {@link SlugRegistryPort.SlugTakenException} si le slug est pris. */
  void insert(Link link);

  /** Met à jour le lien ; un changement de slug passe par le registre (même exception). */
  void update(Link link);

  void delete(UUID id);

  Optional<Link> findById(UUID id);

  /** Liens d'un workspace, plus récents d'abord ; `query` filtre slug, titre et destination. */
  Page page(UUID workspaceId, String query, int limit, String cursor);

  long count(UUID workspaceId);

  long countCreatedSince(UUID workspaceId, Instant since);

  /** Recherche staff, tous workspaces (slug, destination, e-mail du propriétaire). */
  AdminPage adminSearch(String query, UUID workspaceId, int limit, String cursor);

  Optional<AdminRow> adminFind(UUID id);

  record Page(List<Link> items, String nextCursor) {}

  record AdminRow(Link link, String ownerEmail) {}

  record AdminPage(List<AdminRow> items, String nextCursor) {}
}
