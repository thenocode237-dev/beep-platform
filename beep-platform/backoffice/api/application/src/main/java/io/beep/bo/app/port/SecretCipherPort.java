package io.beep.bo.app.port;

public interface SecretCipherPort {
  String encrypt(String plain);

  String decrypt(String cipher);
}
