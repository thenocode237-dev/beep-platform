package io.beep.bo.domain.staff;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP (RFC 6238) : HMAC-SHA1, 6 chiffres, pas de 30 s. */
public final class Totp {
  private static final int STEP_SECONDS = 30;
  private static final int MODULO = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  private Totp() {}

  public static String newSecret() {
    byte[] raw = new byte[20];
    RANDOM.nextBytes(raw);
    return Base32.encode(raw);
  }

  public static String otpauthUri(String issuer, String account, String secret) {
    return "otpauth://totp/" + enc(issuer) + ":" + enc(account) + "?secret=" + secret + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
  }

  public static String code(String secret, Instant at) {
    return code(Base32.decode(secret), at.getEpochSecond() / STEP_SECONDS);
  }

  /** Pas courant ±1, comparaison en temps constant. */
  public static boolean verify(String secret, String candidate, Instant now) {
    if (candidate == null || !candidate.matches("\\d{6}")) return false;
    byte[] key = Base32.decode(secret);
    long counter = now.getEpochSecond() / STEP_SECONDS;
    boolean ok = false;
    for (long c = counter - 1; c <= counter + 1; c++) {
      ok |= MessageDigest.isEqual(code(key, c).getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }
    return ok;
  }

  private static String code(byte[] key, long counter) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
      int offset = hash[hash.length - 1] & 0x0F;
      int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16) | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
      return String.format("%06d", binary % MODULO);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
