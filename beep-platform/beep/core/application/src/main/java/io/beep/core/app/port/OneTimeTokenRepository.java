package io.beep.core.app.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Jetons à usage unique (vérification d'e-mail, réinitialisation de mot de passe). Seule l'empreinte est stockée. */
public interface OneTimeTokenRepository {
  enum Purpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
  }

  void insert(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt);

  /** Consomme atomiquement le jeton s'il est valide, non expiré et non utilisé. */
  Optional<UUID> consume(Purpose purpose, String tokenHash, Instant now);

  int deleteExpired(Instant now);
}
