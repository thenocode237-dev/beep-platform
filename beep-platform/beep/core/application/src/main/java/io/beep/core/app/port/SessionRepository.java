package io.beep.core.app.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
  void insert(Session session);

  /** Retourne la session non expirée correspondant à l'empreinte du jeton. */
  Optional<Session> findActive(String tokenHash, Instant now);

  void delete(String tokenHash);

  void deleteAllForUser(UUID userId);

  int deleteExpired(Instant now);

  record Session(UUID id, UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {}
}
