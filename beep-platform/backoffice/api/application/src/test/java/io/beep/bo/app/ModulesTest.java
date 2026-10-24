package io.beep.bo.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.bo.app.audit.AuditLogUseCase;
import io.beep.bo.app.auth.StaffSettings;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.invitations.InvitationsUseCase;
import io.beep.bo.app.links.LinksModerationUseCase;
import io.beep.bo.app.plans.EntitlementsUseCase;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.users.UserAdminUseCase;
import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Modules de la phase 1 : chaque action exige le bon droit, les décisions sensibles une ré-authentification, et tout est journalisé. */
class ModulesTest {
  static final String SECRET = Totp.newSecret();
  final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
  final List<AuditEvent> audit = new ArrayList<>();
  final AuditPort auditPort = new AuditPort() {
    public void append(AuditEvent e) { audit.add(e); }
    public List<AuditChain.Entry> readAll() { return List.of(); }
    public Page page(int limit, Long before, String action, String actor) { return new Page(List.of(), null); }
  };
  final Map<String, Integer> hits = new HashMap<>();
  final RateLimiterPort limiter = new RateLimiterPort() {
    public boolean tryAcquire(String k, int max, Duration w) { return hits.merge(k, 1, Integer::sum) <= max; }
    public void reset(String k) { hits.remove(k); }
  };
  final SecretCipherPort cipher = new SecretCipherPort() {
    public String encrypt(String p) { return "enc:" + p; }
    public String decrypt(String c) { return c.substring(4); }
  };
  final FakeCore core = new FakeCore();
  final StepUp stepUp = new StepUp(cipher, limiter, clock, StaffSettings.defaults());

  StaffUser staff(Role role) {
    return new StaffUser(UUID.randomUUID(), new Email(role.db() + "@beep.co"), "h", role, "enc:" + SECRET, true, clock.instant());
  }

  String code() {
    return Totp.code(SECRET, clock.instant());
  }

  @Test
  void suspendingAnAccountNeedsTheRightPermissionAReasonAndAFreshTotp() {
    var users = new UserAdminUseCase(core, auditPort, stepUp, clock);
    StaffUser support = staff(Role.SUPPORT);
    assertThatThrownBy(() -> users.setStatus(support, FakeCore.USER, "suspended", "spam", code(), "ip")).hasMessage("forbidden");

    StaffUser ts = staff(Role.TRUST_SAFETY);
    assertThatThrownBy(() -> users.setStatus(ts, FakeCore.USER, "suspended", "", code(), "ip")).hasMessage("invalid_reason");
    assertThatThrownBy(() -> users.setStatus(ts, FakeCore.USER, "suspended", "spam massif", "000000", "ip")).hasMessage("reauth_required");
    assertThat(core.calls).isEmpty(); // rien n'est parti vers le core

    users.setStatus(ts, FakeCore.USER, "suspended", "spam massif", code(), "10.0.0.1");
    assertThat(core.calls).containsExactly("status:suspended:" + ts.id());
    assertThat(audit).singleElement().satisfies(e -> {
      assertThat(e.action()).isEqualTo("users.suspend");
      assertThat(e.target()).isEqualTo("user:" + FakeCore.USER);
      assertThat(e.detail()).contains("motif=spam massif");
      assertThat(e.ip()).isEqualTo("10.0.0.1");
    });
  }

  @Test
  void stepUpIsThrottled() {
    StaffUser ts = staff(Role.TRUST_SAFETY);
    for (int i = 0; i < 5; i++) assertThatThrownBy(() -> stepUp.require(ts, "000000")).hasMessage("reauth_required");
    assertThatThrownBy(() -> stepUp.require(ts, code())).hasMessage("rate_limited");
  }

  @Test
  void viewingAUserIsAudited() {
    new UserAdminUseCase(core, auditPort, stepUp, clock).detail(staff(Role.READONLY), FakeCore.USER, "ip");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("users.view");
  }

  @Test
  void linkModerationIsReservedToTrustAndSafetyAndRecordsTheReason() {
    var links = new LinksModerationUseCase(core, auditPort, clock);
    assertThatThrownBy(() -> links.disable(staff(Role.SUPPORT), FakeCore.LINK, "hameçonnage", "ip")).hasMessage("forbidden");
    StaffUser ts = staff(Role.TRUST_SAFETY);
    assertThat(links.disable(ts, FakeCore.LINK, " hameçonnage signalé ", "ip").status()).isEqualTo("disabled");
    links.enable(ts, FakeCore.LINK, "ip");
    links.search(staff(Role.READONLY), "promo", null, 20, null, "ip");
    assertThat(core.calls).containsExactly("disable:hameçonnage signalé", "enable", "links:promo");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("links.disable", "links.enable", "links.search");
    assertThat(audit.get(0).detail()).contains("slug=promo").contains("motif=hameçonnage signalé");
  }

  @Test
  void overridesNeedReauthenticationAndAreAuditedWithTheirValues() {
    var entitlements = new EntitlementsUseCase(core, auditPort, stepUp, clock);
    UUID ws = UUID.randomUUID();
    assertThatThrownBy(() -> entitlements.setOverrides(staff(Role.FINANCE), ws, Map.of("links.stock_max", 55L), "geste", code(), "ip")).hasMessage("forbidden");
    StaffUser support = staff(Role.SUPPORT);
    Map<String, Long> changes = new HashMap<>();
    changes.put("links.stock_max", 55L);
    changes.put("qr.stock_max", null);
    entitlements.setOverrides(support, ws, changes, "geste commercial", code(), "ip");
    assertThat(audit).singleElement().satisfies(e -> {
      assertThat(e.action()).isEqualTo("entitlements.override");
      assertThat(e.detail()).contains("links.stock_max=55").contains("qr.stock_max=plan").contains("motif=geste commercial");
    });
  }

  @Test
  void auditLogIsVisibleToSuperadminAndTrustSafetyOnlyAndReadingItIsAudited() {
    var log = new AuditLogUseCase(auditPort, clock);
    assertThatThrownBy(() -> log.execute(staff(Role.SUPPORT), 50, null, null, null, "ip")).hasMessage("forbidden");
    assertThat(log.execute(staff(Role.SUPERADMIN), 50, null, "links.", null, "ip").intact()).isTrue();
    assertThat(audit).extracting(AuditEvent::action).containsExactly("audit.read");
  }

  @Test
  void invitationsAreAuditedAndValidatedBeforeReachingTheCore() {
    var invitations = new InvitationsUseCase(core, auditPort, clock);
    assertThatThrownBy(() -> invitations.invite(staff(Role.SUPPORT), "pas-une-adresse", "fr", "ip")).hasMessage("invalid_email");
    invitations.invite(staff(Role.SUPPORT), "Eve@Example.com", "fr", "ip");
    assertThat(core.calls).containsExactly("invite:eve@example.com");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("invitations.create");
    assertThatThrownBy(() -> invitations.list(staff(Role.FINANCE), 20, null)).hasMessage("forbidden");
  }
}
