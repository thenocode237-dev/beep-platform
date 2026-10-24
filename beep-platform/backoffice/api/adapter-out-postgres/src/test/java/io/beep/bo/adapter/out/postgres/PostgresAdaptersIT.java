package io.beep.bo.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** Intégration sur PostgreSQL réel (TEST_DATABASE_URL si défini, sinon Testcontainers). Migrations rejouées de zéro. */
class PostgresAdaptersIT {
  static DSLContext dsl;
  static DataSource ds;
  PostgresAuditAdapter audit;

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
    Flyway f = Flyway.configure().dataSource(ds).cleanDisabled(false).load();
    f.clean();
    f.migrate();
    dsl = DSL.using(ds, SQLDialect.POSTGRES);
  }

  @BeforeEach
  void init() {
    // le journal est append-only (même TRUNCATE est refusé) : on repart d'un schéma propre
    Flyway f = Flyway.configure().dataSource(ds).cleanDisabled(false).load();
    f.clean();
    f.migrate();
    audit = new PostgresAuditAdapter(dsl);
  }

  private static AuditEvent event(String action) {
    return new AuditEvent(Instant.parse("2026-01-01T10:00:00.123456789Z"), UUID.randomUUID(), "a@beep.co", action, "t", "d", "10.0.0.1");
  }

  @Test
  void appendedEntriesFormAVerifiableChainWithoutGaps() {
    for (int i = 0; i < 5; i++) audit.append(event("action-" + i));
    List<AuditChain.Entry> all = audit.readAll();
    assertThat(all).extracting(AuditChain.Entry::seq).containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(AuditChain.verify(all)).isEqualTo(-1);
  }

  @Test
  void concurrentAppendsStayLinear() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Future<?>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < 40; i++) {
      int n = i;
      futures.add(pool.submit(() -> audit.append(event("concurrent-" + n))));
    }
    for (Future<?> f : futures) f.get();
    pool.shutdown();
    List<AuditChain.Entry> all = audit.readAll();
    assertThat(all).hasSize(40);
    assertThat(AuditChain.verify(all)).isEqualTo(-1);
  }

  @Test
  void pageIsNewestFirstWithFiltersAndKeysetCursor() {
    for (int i = 0; i < 3; i++) audit.append(event("links.disable"));
    audit.append(event("users.view"));
    var first = audit.page(2, null, "links.", null);
    assertThat(first.items()).extracting(AuditChain.Entry::seq).containsExactly(3L, 2L);
    var second = audit.page(2, first.nextBeforeSeq(), "links.", null);
    assertThat(second.items()).extracting(AuditChain.Entry::seq).containsExactly(1L);
    assertThat(second.nextBeforeSeq()).isNull();
    assertThat(audit.page(10, null, null, "A@BEEP.CO").items()).hasSize(4);
    assertThat(new PostgresDatabaseStatsAdapter(dsl).databaseBytes()).isPositive();
  }

  @Test
  void databaseRefusesUpdateDeleteAndTruncate() {
    audit.append(event("login"));
    assertThatThrownBy(() -> dsl.execute("UPDATE audit_log SET action = 'forged'")).hasMessageContaining("ajout seul");
    assertThatThrownBy(() -> dsl.execute("DELETE FROM audit_log")).hasMessageContaining("ajout seul");
    assertThatThrownBy(() -> dsl.execute("TRUNCATE audit_log")).hasMessageContaining("ajout seul");
    assertThat(audit.readAll()).hasSize(1);
  }
}
