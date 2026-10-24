package io.beep.core.app.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Jetons à usage unique (vérification d'e-mail, réinitialisation, lien magique, second facteur en attente). Seule l'empreinte est stockée. */
public interface OneTimeTokenRepository {
  enum Purpose {
    VERIFY_EMAIL,
    RESET_PASSWORD,
    MAGIC_LINK,
    /** Premier facteur prouvé (lien magique, OAuth) : le code TOTP reste à saisir. */
    MFA_PENDING
  }

  void insert(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt);

  /** Consomme atomiquement le jeton s'il est valide, non expiré et non utilisé. */
  Optional<UUID> consume(Purpose purpose, String tokenHash, Instant now);

  /** Lit un jeton valide sans le consommer (ex. second facteur : on ne consomme qu'après un code correct). */
  Optional<UUID> find(Purpose purpose, String tokenHash, Instant now);

  int deleteExpired(Instant now);
}
