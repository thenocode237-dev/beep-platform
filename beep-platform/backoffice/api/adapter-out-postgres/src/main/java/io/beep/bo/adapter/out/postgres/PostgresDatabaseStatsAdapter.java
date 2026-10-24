package io.beep.bo.adapter.out.postgres;

import io.beep.bo.app.port.DatabaseStatsPort;
import org.jooq.DSLContext;

public class PostgresDatabaseStatsAdapter implements DatabaseStatsPort {
  private final DSLContext dsl;

  public PostgresDatabaseStatsAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public long databaseBytes() {
    return dsl.fetchOne("SELECT pg_database_size(current_database())").get(0, Long.class);
  }
}
