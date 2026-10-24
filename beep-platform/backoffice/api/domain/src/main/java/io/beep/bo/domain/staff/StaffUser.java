package io.beep.bo.domain.staff;

import io.beep.bo.domain.DomainException;
import java.time.Instant;
import java.util.UUID;

/** Membre du personnel. Le TOTP est obligatoire : un compte sans secret ne peut pas se connecter. */
public record StaffUser(UUID id, Email email, String passwordHash, Role role, String totpSecretCipher, boolean active, Instant createdAt) {

  public boolean canLogin() {
    return active && totpSecretCipher != null;
  }

  public void require(Permission p) {
    if (!role.can(p)) throw new DomainException("forbidden");
  }
}
