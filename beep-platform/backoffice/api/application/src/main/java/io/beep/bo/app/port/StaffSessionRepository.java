package io.beep.bo.app.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sessions staff courtes : durée absolue ET inactivité maximale. */
public interface StaffSessionRepository {
  void insert(Session session);

  /** Session valide : non expirée et active depuis {@code idleCutoff}. */
  Optional<Session> findActive(String tokenHash, Instant now, Instant idleCutoff);

  void touch(String tokenHash, Instant now);

  void delete(String tokenHash);

  int deleteExpired(Instant now, Instant idleCutoff);

  record Session(UUID id, UUID staffId, String tokenHash, Instant createdAt, Instant lastSeenAt, Instant expiresAt) {}
}
