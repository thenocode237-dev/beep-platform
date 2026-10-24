package io.beep.core.app.port;

/** Chiffrement réversible des secrets au repos (secrets TOTP). */
public interface SecretCipherPort {
  String encrypt(String plain);

  String decrypt(String cipher);
}
