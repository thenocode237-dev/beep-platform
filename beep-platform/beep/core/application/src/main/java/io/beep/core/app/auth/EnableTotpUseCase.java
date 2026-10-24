package io.beep.core.app.auth;

import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;
import java.time.Clock;

/** Étape 2 : active le TOTP si le code saisi correspond au secret généré. */
public class EnableTotpUseCase {
  private final UserRepository users;
  private final SecretCipherPort cipher;
  private final Clock clock;

  public EnableTotpUseCase(UserRepository users, SecretCipherPort cipher, Clock clock) {
    this.users = users;
    this.cipher = cipher;
    this.clock = clock;
  }

  public void execute(User user, String code) {
    if (user.totpEnabled()) throw new DomainException("totp_already_enabled");
    if (user.totpSecretCipher() == null) throw new DomainException("invalid_totp");
    if (!Totp.verify(cipher.decrypt(user.totpSecretCipher()), code, clock.instant())) throw new DomainException("invalid_totp");
    users.update(user.withTotp(user.totpSecretCipher(), true));
  }
}
