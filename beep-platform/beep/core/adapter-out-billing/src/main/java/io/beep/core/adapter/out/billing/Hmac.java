package io.beep.core.adapter.out.billing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 hexadécimal et comparaison à temps constant des signatures de notification. */
final class Hmac {
  private Hmac() {}

  static String sha256Hex(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] out = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(out.length * 2);
      for (byte b : out) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean valid(String secret, String body, String signature) {
    if (secret == null || secret.isBlank() || signature == null) return false;
    return MessageDigest.isEqual(sha256Hex(secret, body).getBytes(StandardCharsets.US_ASCII), signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
  }
}
