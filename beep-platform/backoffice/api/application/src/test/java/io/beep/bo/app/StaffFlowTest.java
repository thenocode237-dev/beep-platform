package io.beep.bo.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.bo.app.auth.StaffAuthenticateUseCase;
import io.beep.bo.app.auth.StaffLoginUseCase;
import io.beep.bo.app.auth.StaffSettings;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.port.PasswordHasherPort;
import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.app.port.StaffSessionRepository;
import io.beep.bo.app.port.TokenPort;
import io.beep.bo.app.users.ListUsersUseCase;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffFlowTest {
  static final String PASSWORD = "staff-password-1234";
  static final String SECRET = Totp.newSecret();

  Instant[] now = {Instant.parse("2026-01-01T10:00:00Z")};
  Clock clock = new Clock() {
    public ZoneId getZone() { return ZoneId.of("UTC"); }
    public Clock withZone(ZoneId z) { return this; }
    public Instant instant() { return now[0]; }
  };

  Map<UUID, StaffUser> users = new HashMap<>();
  Map<String, StaffSessionRepository.Session> sessions = new HashMap<>();
  List<AuditEvent> audit = new ArrayList<>();
  Map<String, Integer> hits = new HashMap<>();
  StaffSettings settings = StaffSettings.defaults();

  StaffRepository staffRepo = new StaffRepository() {
    public Optional<StaffUser> findById(UUID id) { return Optional.ofNullable(users.get(id)); }
    public Optional<StaffUser> findByEmail(Email e) { return users.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
    public void insert(StaffUser u) { users.put(u.id(), u); }
  };
  StaffSessionRepository sessionRepo = new StaffSessionRepository() {
    public void insert(Session s) { sessions.put(s.tokenHash(), s); }
    public Optional<Session> findActive(String h, Instant n, Instant idle) {
      return Optional.ofNullable(sessions.get(h)).filter(s -> s.expiresAt().isAfter(n) && s.lastSeenAt().isAfter(idle));
    }
    public void touch(String h, Instant n) {
      Session s = sessions.get(h);
      sessions.put(h, new Session(s.id(), s.staffId(), s.tokenHash(), s.createdAt(), n, s.expiresAt()));
    }
    public void delete(String h) { sessions.remove(h); }
    public int deleteExpired(Instant n, Instant idle) { return 0; }
  };
  AuditPort auditPort = new AuditPort() {
    public void append(AuditEvent e) { audit.add(e); }
    public List<AuditChain.Entry> readAll() { return List.of(); }
    public Page page(int limit, Long before, String action, String actor) { return new Page(List.of(), null); }
  };
  PasswordHasherPort hasher = new PasswordHasherPort() {
    public String hash(String raw) { return "h:" + raw; }
    public boolean matches(String raw, String hash) { return hash.equals("h:" + raw); }
  };
  int tokenCounter;
  TokenPort tokens = new TokenPort() {
    public String newToken() { return "tok" + (++tokenCounter); }
    public String hash(String t) { return "hash:" + t; }
  };
  SecretCipherPort cipher = new SecretCipherPort() {
    public String encrypt(String p) { return "enc:" + p; }
    public String decrypt(String c) { return c.substring(4); }
  };
  RateLimiterPort limiter = new RateLimiterPort() {
    public boolean tryAcquire(String k, int max, Duration w) { return hits.merge(k, 1, Integer::sum) <= max; }
    public void reset(String k) { hits.remove(k); }
  };
  CoreGatewayPort core = new FakeCore();

  StaffLoginUseCase login;
  StaffAuthenticateUseCase authenticate;
  StaffUser admin;

  @BeforeEach
  void setUp() {
    login = new StaffLoginUseCase(staffRepo, sessionRepo, hasher, tokens, cipher, limiter, auditPort, clock, settings);
    authenticate = new StaffAuthenticateUseCase(sessionRepo, staffRepo, tokens, clock, settings);
    admin = new StaffUser(UUID.randomUUID(), new Email("admin@beep.co"), hasher.hash(PASSWORD), Role.SUPERADMIN, cipher.encrypt(SECRET), true, now[0]);
    users.put(admin.id(), admin);
  }

  private StaffLoginUseCase.Command cmd(String pwd, String totp) {
    return new StaffLoginUseCase.Command("admin@beep.co", pwd, totp, "10.0.0.1");
  }

  @Test
  void loginNeedsPasswordAndTotpAndIsAudited() {
    var result = login.execute(cmd(PASSWORD, Totp.code(SECRET, now[0])));
    assertThat(authenticate.execute(result.sessionToken())).isPresent();
    assertThat(audit).extracting(AuditEvent::action).containsExactly("staff.login");
  }

  @Test
  void everyFailureLooksTheSameAndIsAudited() {
    String good = Totp.code(SECRET, now[0]);
    assertThatThrownBy(() -> login.execute(cmd("wrong-password-1234", good))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(cmd(PASSWORD, "000000"))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(cmd(PASSWORD, null))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(new StaffLoginUseCase.Command("ghost@beep.co", PASSWORD, good, "10.0.0.1"))).hasMessage("invalid_credentials");
    assertThat(audit).extracting(AuditEvent::action).containsOnly("staff.login_failed");
    assertThat(audit).hasSize(4);
  }

  @Test
  void disabledAccountCannotLoginEvenWithValidCredentials() {
    users.put(admin.id(), new StaffUser(admin.id(), admin.email(), admin.passwordHash(), admin.role(), admin.totpSecretCipher(), false, admin.createdAt()));
    assertThatThrownBy(() -> login.execute(cmd(PASSWORD, Totp.code(SECRET, now[0])))).hasMessage("invalid_credentials");
  }

  @Test
  void loginIsThrottledAndTheThrottleIsAudited() {
    for (int i = 0; i < 5; i++) assertThatThrownBy(() -> login.execute(cmd("wrong-password-1234", "000000"))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(cmd(PASSWORD, Totp.code(SECRET, now[0])))).hasMessage("rate_limited");
    assertThat(audit).extracting(AuditEvent::action).last().isEqualTo("staff.login_throttled");
  }

  @Test
  void sessionExpiresAfterThirtyMinutesOfInactivityButActivityKeepsItAlive() {
    String token = login.execute(cmd(PASSWORD, Totp.code(SECRET, now[0]))).sessionToken();
    now[0] = now[0].plus(Duration.ofMinutes(20));
    assertThat(authenticate.execute(token)).isPresent(); // activité à +20 min
    now[0] = now[0].plus(Duration.ofMinutes(20));
    assertThat(authenticate.execute(token)).isPresent(); // +40 min au total, mais actif il y a 20 min
    now[0] = now[0].plus(Duration.ofMinutes(31));
    assertThat(authenticate.execute(token)).isEmpty();   // inactif depuis 31 min
  }

  @Test
  void sessionHasAnAbsoluteLifetimeOfEightHours() {
    String token = login.execute(cmd(PASSWORD, Totp.code(SECRET, now[0]))).sessionToken();
    for (int i = 0; i < 17; i++) { // activité toutes les 29 min : jamais inactif
      now[0] = now[0].plus(Duration.ofMinutes(29));
      authenticate.execute(token);
    }
    assertThat(now[0]).isAfter(Instant.parse("2026-01-01T18:00:00Z"));
    assertThat(authenticate.execute(token)).isEmpty();
  }

  @Test
  void listingUsersRequiresPermissionAndIsAudited() {
    var listUsers = new ListUsersUseCase(core, auditPort, clock);
    listUsers.execute(admin, 20, null, "10.0.0.1");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("users.list");

    StaffUser finance = new StaffUser(UUID.randomUUID(), new Email("fin@beep.co"), "h", Role.FINANCE, "c", true, now[0]);
    assertThatThrownBy(() -> listUsers.execute(finance, 20, null, "10.0.0.1")).isInstanceOf(DomainException.class).hasMessage("forbidden");
    assertThat(audit).hasSize(1); // pas d'accès, pas d'entrée de lecture
  }
}
