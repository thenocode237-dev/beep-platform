package io.beep.core.app.port;

import io.beep.core.domain.customdomain.CustomDomain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CustomDomainRepository {
  /** Lève {@link DuplicateHostnameException} si le nom est déjà enregistré (par n'importe quel workspace). */
  void insert(CustomDomain domain);

  Optional<CustomDomain> findById(UUID id);

  Optional<CustomDomain> findByHostname(String hostname);

  /** Verrouille la ligne jusqu'à la fin de la transaction. */
  Optional<CustomDomain> lockById(UUID id);

  void update(CustomDomain domain);

  /** Supprime le domaine ; échoue (IllegalStateException) s'il porte encore des liens : le cas d'usage le vérifie avant. */
  void delete(UUID id);

  List<CustomDomain> forWorkspace(UUID workspaceId);

  long count(UUID workspaceId);

  long linkCount(UUID domainId);

  /** Défaut du workspace : un seul à la fois ; `null` retire le défaut. */
  void setDefault(UUID workspaceId, UUID domainId);

  Optional<CustomDomain> defaultOf(UUID workspaceId);


  Map<UUID, String> hostnames(Set<UUID> ids);

  /** Liste staff : tous workspaces, filtrable par nom d'hôte et état. */
  AdminPage adminPage(String query, String status, int limit, String cursor);

  Optional<AdminRow> adminFind(UUID id);

  record AdminRow(CustomDomain domain, String ownerEmail, long linkCount) {}

  record AdminPage(List<AdminRow> items, String nextCursor) {}

  class DuplicateHostnameException extends RuntimeException {
    public DuplicateHostnameException(Throwable cause) {
      super(cause);
    }
  }
}
