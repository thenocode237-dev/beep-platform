package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Les migrations s'appliquent de zéro sur un PostgreSQL réel, passent la validation Flyway et sont idempotentes. */
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
      flyway.validate();                                      // sommes de contrôle et ordre cohérents
      assertThat(flyway.migrate().migrationsExecuted).isZero(); // rejouer ne fait rien
      assertThat(flyway.info().pending()).isEmpty();
    } finally {
      if (container != null) container.stop();
    }
  }
}
