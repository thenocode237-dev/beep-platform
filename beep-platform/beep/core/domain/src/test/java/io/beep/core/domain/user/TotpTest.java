package io.beep.core.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {
  // Vecteurs de test RFC 6238 (SHA1, secret ASCII « 12345678901234567890 »), tronqués à 6 chiffres.
  private static final String RFC_SECRET = Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

  @Test
  void matchesRfc6238Vectors() {
    assertThat(Totp.code(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
    assertThat(Totp.code(RFC_SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
    assertThat(Totp.code(RFC_SECRET, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
  }

  @Test
  void toleratesOneStepOfClockDriftButNotTwo() {
    Instant t = Instant.ofEpochSecond(1_700_000_000L);
    String code = Totp.code(RFC_SECRET, t);
    assertThat(Totp.verify(RFC_SECRET, code, t.plusSeconds(30))).isTrue();
    assertThat(Totp.verify(RFC_SECRET, code, t.minusSeconds(30))).isTrue();
    assertThat(Totp.verify(RFC_SECRET, code, t.plusSeconds(90))).isFalse();
  }

  @Test
  void rejectsMalformedCodes() {
    assertThat(Totp.verify(RFC_SECRET, "12345", Instant.now())).isFalse();
    assertThat(Totp.verify(RFC_SECRET, "abcdef", Instant.now())).isFalse();
    assertThat(Totp.verify(RFC_SECRET, null, Instant.now())).isFalse();
  }

  @Test
  void base32RoundTrips() {
    byte[] data = {1, 2, 3, 4, 5, (byte) 250, 99};
    assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
  }

  @Test
  void generatesDistinctSecrets() {
    assertThat(Totp.newSecret()).hasSize(32).isNotEqualTo(Totp.newSecret());
  }
}
