package io.beep.bo.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Les migrations du back office s'appliquent de zéro sur un PostgreSQL réel, valident et sont idempotentes. */
class MigrationIT {
  @Test
  void migrateFromScratchValidateAndReplay() {
    PGSimpleDataSource ds = new PGSimpleDataSource();
    String url = System.getenv("TEST_DATABASE_URL");
    PostgreSQLContainer<?> container = null;
    if (url != null && !url.isBlank()) {
      ds.setUrl(url);
    } else {
      container = new PostgreSQLContainer<>("postgres:16-alpine");
      container.start();
      ds.setUrl(container.getJdbcUrl());
      ds.setUser(container.getUsername());
      ds.setPassword(container.getPassword());
    }
    try {
      Flyway flyway = Flyway.configure().dataSource(ds).cleanDisabled(false).load();
      flyway.clean();
      var first = flyway.migrate();
      assertThat(first.success).isTrue();
      assertThat(first.migrationsExecuted).isPositive();
      flyway.validate();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(flyway.info().pending()).isEmpty();
    } finally {
      if (container != null) container.stop();
    }
  }
}
