package io.beep.bo.app.port;

public interface PasswordHasherPort {
  String hash(String raw);

  boolean matches(String raw, String hash);
}
