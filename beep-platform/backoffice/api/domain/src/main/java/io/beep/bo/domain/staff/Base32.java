package io.beep.bo.domain.staff;

import java.util.Locale;

/** Base32 RFC 4648 (sans remplissage), alphabet des applications d'authentification. */
final class Base32 {
  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  private Base32() {}

  static String encode(byte[] data) {
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        out.append(ALPHABET[(buffer >> (bits - 5)) & 31]);
        bits -= 5;
      }
      buffer &= (1 << bits) - 1;
    }
    if (bits > 0) out.append(ALPHABET[(buffer << (5 - bits)) & 31]);
    return out.toString();
  }

  static byte[] decode(String text) {
    String s = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
    byte[] out = new byte[s.length() * 5 / 8];
    int buffer = 0;
    int bits = 0;
    int idx = 0;
    for (char c : s.toCharArray()) {
      int v = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
      if (v < 0) throw new IllegalArgumentException("base32");
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out[idx++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
        bits -= 8;
        buffer &= (1 << bits) - 1;
      }
    }
    return out;
  }
}
