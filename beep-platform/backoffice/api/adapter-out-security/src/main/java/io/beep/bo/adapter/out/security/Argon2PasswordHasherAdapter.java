package io.beep.bo.adapter.out.security;

import io.beep.bo.app.port.PasswordHasherPort;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Argon2id, paramètres OWASP (m = 19 Mio, t = 2, p = 1). */
public class Argon2PasswordHasherAdapter implements PasswordHasherPort {
  private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);

  @Override
  public String hash(String raw) {
    return encoder.encode(raw);
  }

  @Override
  public boolean matches(String raw, String hash) {
    return encoder.matches(raw, hash);
  }
}
