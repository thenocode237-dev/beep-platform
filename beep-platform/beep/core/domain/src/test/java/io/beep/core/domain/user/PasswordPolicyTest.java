package io.beep.core.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.DomainException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
  @Test
  void requiresTwelveCharacters() {
    assertThatThrownBy(() -> PasswordPolicy.check("short")).isInstanceOf(DomainException.class).hasMessage("weak_password");
    assertThatCode(() -> PasswordPolicy.check("a-long-enough-passphrase")).doesNotThrowAnyException();
  }

  @Test
  void capsLengthToBoundHashingCost() {
    assertThatThrownBy(() -> PasswordPolicy.check("x".repeat(129))).isInstanceOf(DomainException.class);
  }
}
