package io.beep.core.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Utilisateur de la plateforme. Immuable : chaque transition retourne une nouvelle instance. */
public record User(
    UUID id,
    Email email,
    String passwordHash, // null : compte créé par lien magique ou OAuth (mot de passe définissable par réinitialisation)
    UserStatus status,
    Instant emailVerifiedAt,
    String totpSecretCipher, // secret TOTP chiffré (jamais en clair)
    boolean totpEnabled,
    String locale,
    Instant createdAt) {

  public static User register(UUID id, Email email, String passwordHash, String locale, Instant now) {
    return new User(id, email, passwordHash, UserStatus.PENDING, null, null, false, locale, now);
  }

  /** Compte sans mot de passe (lien magique, OAuth) : actif dès que l'adresse est prouvée. */
  public static User registerExternal(UUID id, Email email, String locale, Instant now) {
    return new User(id, email, null, UserStatus.PENDING, null, null, false, locale, now);
  }

  public User withStatus(UserStatus next) {
    return new User(id, email, passwordHash, next, emailVerifiedAt, totpSecretCipher, totpEnabled, locale, createdAt);
  }

  public boolean emailVerified() {
    return emailVerifiedAt != null;
  }

  public User verifyEmail(Instant now) {
    UserStatus next = status == UserStatus.PENDING ? UserStatus.ACTIVE : status;
    return new User(id, email, passwordHash, next, emailVerifiedAt != null ? emailVerifiedAt : now, totpSecretCipher, totpEnabled, locale, createdAt);
  }

  public User withPasswordHash(String hash) {
    return new User(id, email, hash, status, emailVerifiedAt, totpSecretCipher, totpEnabled, locale, createdAt);
  }

  public User withTotp(String secretCipher, boolean enabled) {
    return new User(id, email, passwordHash, status, emailVerifiedAt, secretCipher, enabled, locale, createdAt);
  }

  public boolean canLogin() {
    return status == UserStatus.ACTIVE && emailVerified();
  }
}
