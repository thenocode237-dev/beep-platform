package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.SessionRepository.Session;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Intégration sur un PostgreSQL réel, migrations Flyway rejouées de zéro.
 * Utilise TEST_DATABASE_URL (jdbc:postgresql://…?user=…&password=…) s'il est défini (CI, Compose), sinon Testcontainers.
 */
class PostgresAdaptersIT {
  static DataSource ds;
  static DSLContext dsl;

  @BeforeAll
  static void start() {
    PGSimpleDataSource pg = new PGSimpleDataSource();
    String url = System.getenv("TEST_DATABASE_URL");
    if (url != null && !url.isBlank()) {
      pg.setUrl(url);
    } else {
      PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine");
      c.start();
      pg.setUrl(c.getJdbcUrl());
      pg.setUser(c.getUsername());
      pg.setPassword(c.getPassword());
    }
    ds = pg;
    Flyway flyway = Flyway.configure().dataSource(ds).cleanDisabled(false).load();
    flyway.clean();
    flyway.migrate();
    dsl = DSL.using(ds, SQLDialect.POSTGRES);
  }

  PostgresUserAdapter users;
  PostgresSessionAdapter sessions;
  PostgresOneTimeTokenAdapter tokens;

  @BeforeEach
  void init() {
    dsl.execute("TRUNCATE users, slugs CASCADE");
    users = new PostgresUserAdapter(dsl);
    sessions = new PostgresSessionAdapter(dsl);
    tokens = new PostgresOneTimeTokenAdapter(dsl);
  }

  private User newUser(String email, Instant at) {
    return User.register(UUID.randomUUID(), new Email(email), "$argon2id$x", "fr", at);
  }

  @Test
  void roundTripsAUserAndEnforcesUniqueEmail() {
    User u = newUser("ada@example.com", Instant.parse("2026-01-01T10:00:00.123456Z"));
    users.insert(u);
    assertThat(users.findByEmail(new Email("ADA@example.com"))).contains(u);
    assertThatThrownBy(() -> users.insert(newUser("ada@example.com", Instant.now()))).isInstanceOf(UserRepository.DuplicateEmailException.class);
  }

  @Test
  void updatesVerificationAndTotp() {
    User u = newUser("ada@example.com", Instant.parse("2026-01-01T10:00:00Z"));
    users.insert(u);
    User updated = u.verifyEmail(Instant.parse("2026-01-02T10:00:00Z")).withTotp("cipher", true);
    users.update(updated);
    assertThat(users.findById(u.id())).contains(updated);
    assertThat(users.findById(u.id()).orElseThrow().canLogin()).isTrue();
  }

  @Test
  void paginatesByCursorNewestFirstWithoutGapsOrDuplicates() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < 5; i++) users.insert(newUser("u" + i + "@example.com", base.plusSeconds(i)));
    UserRepository.Page p1 = users.page(2, null);
    UserRepository.Page p2 = users.page(2, p1.nextCursor());
    UserRepository.Page p3 = users.page(2, p2.nextCursor());
    assertThat(p1.items()).extracting(x -> x.email().value()).containsExactly("u4@example.com", "u3@example.com");
    assertThat(p2.items()).extracting(x -> x.email().value()).containsExactly("u2@example.com", "u1@example.com");
    assertThat(p3.items()).extracting(x -> x.email().value()).containsExactly("u0@example.com");
    assertThat(p3.nextCursor()).isNull();
  }

  @Test
  void sessionsExpireAndCanBeRevoked() {
    User u = newUser("ada@example.com", Instant.parse("2026-01-01T00:00:00Z"));
    users.insert(u);
    Instant now = Instant.parse("2026-01-01T12:00:00Z");
    sessions.insert(new Session(UUID.randomUUID(), u.id(), "h1", now, now.plus(Duration.ofDays(1))));
    sessions.insert(new Session(UUID.randomUUID(), u.id(), "h2", now, now.minusSeconds(1)));
    assertThat(sessions.findActive("h1", now)).isPresent();
    assertThat(sessions.findActive("h2", now)).isEmpty();
    assertThat(sessions.deleteExpired(now)).isEqualTo(1);
    sessions.deleteAllForUser(u.id());
    assertThat(sessions.findActive("h1", now)).isEmpty();
  }

  @Test
  void oneTimeTokenIsConsumedExactlyOnce() {
    User u = newUser("ada@example.com", Instant.parse("2026-01-01T00:00:00Z"));
    users.insert(u);
    Instant now = Instant.parse("2026-01-01T12:00:00Z");
    tokens.insert(u.id(), Purpose.VERIFY_EMAIL, "th", now.plusSeconds(60));
    assertThat(tokens.consume(Purpose.RESET_PASSWORD, "th", now)).isEmpty(); // mauvais usage
    assertThat(tokens.consume(Purpose.VERIFY_EMAIL, "th", now)).contains(u.id());
    assertThat(tokens.consume(Purpose.VERIFY_EMAIL, "th", now)).isEmpty();   // déjà utilisé
    tokens.insert(u.id(), Purpose.VERIFY_EMAIL, "old", now.minusSeconds(1));
    assertThat(tokens.consume(Purpose.VERIFY_EMAIL, "old", now)).isEmpty();  // expiré
  }
}
