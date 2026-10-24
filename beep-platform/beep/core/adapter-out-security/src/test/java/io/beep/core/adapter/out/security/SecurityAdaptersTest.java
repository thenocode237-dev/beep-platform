package io.beep.core.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecurityAdaptersTest {
  @Test
  void argon2idHashesAreSaltedAndVerifiable() {
    var hasher = new Argon2PasswordHasherAdapter();
    String a = hasher.hash("correct-horse-battery");
    String b = hasher.hash("correct-horse-battery");
    assertThat(a).startsWith("$argon2id$").isNotEqualTo(b);
    assertThat(hasher.matches("correct-horse-battery", a)).isTrue();
    assertThat(hasher.matches("wrong", a)).isFalse();
  }

  @Test
  void tokensAreUniqueAndHashingIsDeterministic() {
    var tokens = new SecureTokenAdapter();
    String t = tokens.newToken();
    assertThat(t).hasSize(43).isNotEqualTo(tokens.newToken());
    assertThat(tokens.hash(t)).isEqualTo(tokens.hash(t)).hasSize(64).isNotEqualTo(t);
  }

  @Test
  void aesGcmRoundTripsAndDetectsTampering() {
    var cipher = new AesGcmSecretCipherAdapter(Base64.getEncoder().encodeToString(new byte[32]));
    String enc = cipher.encrypt("JBSWY3DPEHPK3PXP");
    assertThat(enc).doesNotContain("JBSWY3DP");
    assertThat(cipher.decrypt(enc)).isEqualTo("JBSWY3DPEHPK3PXP");
    byte[] raw = Base64.getDecoder().decode(enc);
    raw[raw.length - 1] ^= 1;
    assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rateLimiterUsesASlidingWindow() {
    var now = new Instant[] {Instant.parse("2026-01-01T00:00:00Z")};
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now[0]; }
    };
    var limiter = new InMemoryRateLimiterAdapter(clock);
    Duration window = Duration.ofMinutes(10);
    assertThat(limiter.tryAcquire("k", 2, window)).isTrue();
    assertThat(limiter.tryAcquire("k", 2, window)).isTrue();
    assertThat(limiter.tryAcquire("k", 2, window)).isFalse();
    assertThat(limiter.tryAcquire("other", 2, window)).isTrue();
    now[0] = now[0].plus(Duration.ofMinutes(11));
    assertThat(limiter.tryAcquire("k", 2, window)).isTrue();
    limiter.reset("k");
    assertThat(limiter.tryAcquire("k", 1, window)).isTrue();
  }
}
