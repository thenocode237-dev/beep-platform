package io.beep.core.app.auth;

import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;

/** Étape 1 : génère un secret (non actif tant que l'utilisateur n'a pas prouvé qu'il le possède). */
public class SetupTotpUseCase {
  public record Setup(String secret, String otpauthUri) {}

  private final UserRepository users;
  private final SecretCipherPort cipher;
  private final AuthSettings settings;

  public SetupTotpUseCase(UserRepository users, SecretCipherPort cipher, AuthSettings settings) {
    this.users = users;
    this.cipher = cipher;
    this.settings = settings;
  }

  public Setup execute(User user) {
    if (user.totpEnabled()) throw new DomainException("totp_already_enabled");
    String secret = Totp.newSecret();
    users.update(user.withTotp(cipher.encrypt(secret), false));
    return new Setup(secret, Totp.otpauthUri(settings.totpIssuer(), user.email().value(), secret));
  }
}
