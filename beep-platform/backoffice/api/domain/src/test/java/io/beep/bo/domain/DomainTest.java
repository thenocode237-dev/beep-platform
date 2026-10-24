package io.beep.bo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainTest {
  private static AuditEvent event(String action) {
    return new AuditEvent(Instant.parse("2026-01-01T10:00:00.123456789Z"), UUID.fromString("00000000-0000-0000-0000-000000000001"), "a@b.co", action, "user:1", "x", "10.0.0.1");
  }

  private static List<AuditChain.Entry> chain(int n) {
    List<AuditChain.Entry> out = new ArrayList<>();
    String prev = AuditChain.GENESIS;
    for (int i = 1; i <= n; i++) {
      AuditEvent e = event("action-" + i);
      String h = AuditChain.hash(prev, e);
      out.add(new AuditChain.Entry(i, e, prev, h));
      prev = h;
    }
    return out;
  }

  @Test
  void intactChainVerifies() {
    assertThat(AuditChain.verify(chain(5))).isEqualTo(-1);
  }

  @Test
  void tamperingWithAnyEntryIsDetectedAtThatEntry() {
    List<AuditChain.Entry> c = chain(5);
    AuditChain.Entry third = c.get(2);
    c.set(2, new AuditChain.Entry(3, event("forged"), third.prevHash(), third.hash()));
    assertThat(AuditChain.verify(c)).isEqualTo(3);
  }

  @Test
  void deletingAnEntryBreaksTheChain() {
    List<AuditChain.Entry> c = chain(5);
    c.remove(1);
    assertThat(AuditChain.verify(c)).isEqualTo(3);
  }

  @Test
  void rolesGrantOnlyWhatTheyShould() {
    assertThat(Role.SUPERADMIN.can(Permission.USERS_READ)).isTrue();
    assertThat(Role.SUPPORT.can(Permission.USERS_READ)).isTrue();
    assertThat(Role.FINANCE.can(Permission.USERS_READ)).isFalse();
    StaffUser finance = new StaffUser(UUID.randomUUID(), new Email("f@beep.co"), "h", Role.FINANCE, "c", true, Instant.now());
    assertThatThrownBy(() -> finance.require(Permission.USERS_READ)).hasMessage("forbidden");
  }

  @Test
  void staffWithoutTotpCannotLogin() {
    assertThat(new StaffUser(UUID.randomUUID(), new Email("s@beep.co"), "h", Role.SUPPORT, null, true, Instant.now()).canLogin()).isFalse();
    assertThat(new StaffUser(UUID.randomUUID(), new Email("s@beep.co"), "h", Role.SUPPORT, "c", false, Instant.now()).canLogin()).isFalse();
  }

  @Test
  void totpToleratesOneStepOfDrift() {
    String secret = Totp.newSecret();
    Instant t = Instant.ofEpochSecond(1_700_000_000L);
    String code = Totp.code(secret, t);
    assertThat(Totp.verify(secret, code, t.plusSeconds(30))).isTrue();
    assertThat(Totp.verify(secret, code, t.plusSeconds(120))).isFalse();
  }
}
