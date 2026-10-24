package io.beep.core.adapter.out.security;

import io.beep.core.app.port.TokenPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class SecureTokenAdapter implements TokenPort {
  private final SecureRandom random = new SecureRandom();

  @Override
  public String newToken() {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  /** SHA-256 : suffisant pour un jeton à 256 bits d'entropie (pas de dictionnaire à attaquer). */
  @Override
  public String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
