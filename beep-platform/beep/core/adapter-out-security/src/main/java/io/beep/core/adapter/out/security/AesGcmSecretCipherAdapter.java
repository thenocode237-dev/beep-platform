package io.beep.core.adapter.out.security;

import io.beep.core.app.port.SecretCipherPort;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM. Format stocké : base64(iv[12] || texte chiffré || tag). La clé (32 octets, base64) vient de l'environnement. */
public class AesGcmSecretCipherAdapter implements SecretCipherPort {
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmSecretCipherAdapter(String base64Key) {
    byte[] raw = Base64.getDecoder().decode(base64Key);
    if (raw.length != 32) throw new IllegalArgumentException("TOTP_ENCRYPTION_KEY doit faire 32 octets (base64)");
    this.key = new SecretKeySpec(raw, "AES");
  }

  @Override
  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String decrypt(String cipher) {
    try {
      byte[] all = Base64.getDecoder().decode(cipher);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
      return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), java.nio.charset.StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
