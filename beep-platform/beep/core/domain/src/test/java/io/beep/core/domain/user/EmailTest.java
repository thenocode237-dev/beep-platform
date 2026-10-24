package io.beep.core.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.DomainException;
import org.junit.jupiter.api.Test;

class EmailTest {
  @Test
  void normalizesCaseAndWhitespace() {
    assertThat(new Email("  Ada@Example.COM ").value()).isEqualTo("ada@example.com");
  }

  @Test
  void rejectsMalformedAddresses() {
    for (String bad : new String[] {"", "no-at", "a@b", "a b@c.de", "@x.fr", null}) {
      assertThatThrownBy(() -> new Email(bad)).isInstanceOf(DomainException.class).hasMessage("invalid_email");
    }
  }
}
