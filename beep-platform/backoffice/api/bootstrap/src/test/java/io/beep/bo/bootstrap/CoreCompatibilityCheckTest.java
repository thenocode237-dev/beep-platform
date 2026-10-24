package io.beep.bo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreCompatibilityCheckTest {
  @Test
  void acceptsSameMajorAndEqualOrNewerMinor() {
    assertThat(CoreCompatibilityCheck.compatible("0.2", "0.2.0")).isTrue();
    assertThat(CoreCompatibilityCheck.compatible("0.1", "0.2.3")).isTrue();
  }

  @Test
  void rejectsOlderMinorOrDifferentMajor() {
    assertThat(CoreCompatibilityCheck.compatible("0.2", "0.1.9")).isFalse();
    assertThat(CoreCompatibilityCheck.compatible("0.1", "1.0.0")).isFalse();
  }
}
