package io.beep.bo.app.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seul accès aux données métier de beep-core : l'API admin interne (contrat OpenAPI). Jamais la base de Beep.
 */
public interface CoreGatewayPort {
  UsersPage listUsers(int limit, String cursor, UUID staffId);

  /** Version du contrat admin implémentée par le core (ex. « 0.1.0 »). */
  String contractVersion();

  boolean isReachable();

  record UserView(UUID id, String email, String status, boolean emailVerified, boolean totpEnabled, Instant createdAt) {}

  record UsersPage(List<UserView> items, String nextCursor) {}

  /** Le core ne répond pas : le back office reste utilisable en mode dégradé. */
  class CoreUnavailableException extends RuntimeException {
    public CoreUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
