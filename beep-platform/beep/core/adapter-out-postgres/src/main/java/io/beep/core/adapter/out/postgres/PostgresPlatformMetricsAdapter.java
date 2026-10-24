package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.PlatformMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;

public class PostgresPlatformMetricsAdapter implements PlatformMetricsRepository {
  private final DSLContext dsl;

  public PostgresPlatformMetricsAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public UserCounts userCounts() {
    var r = dsl.fetchOne("SELECT count(*), count(*) FILTER (WHERE status = 'active'), count(*) FILTER (WHERE status = 'pending'), "
        + "count(*) FILTER (WHERE status = 'suspended') FROM users");
    return new UserCounts(r.get(0, Long.class), r.get(1, Long.class), r.get(2, Long.class), r.get(3, Long.class));
  }

  @Override
  public List<DayCount> signupsPerDay(LocalDate from, LocalDate to) {
    return dsl.fetch("SELECT d::date, COALESCE(u.n, 0) FROM generate_series(?::date, ?::date, interval '1 day') d "
            + "LEFT JOIN (SELECT (created_at AT TIME ZONE 'UTC')::date AS day, count(*) AS n FROM users GROUP BY 1) u ON u.day = d::date ORDER BY 1",
        from, to).map(r -> new DayCount(r.get(0, LocalDate.class), r.get(1, Long.class)));
  }

  @Override
  public Activation activation(Instant since, Duration window) {
    var r = dsl.fetchOne("SELECT count(*), count(*) FILTER (WHERE w.first_link_at IS NOT NULL AND w.first_link_at <= u.created_at + make_interval(secs => ?)) "
        + "FROM users u LEFT JOIN workspaces w ON w.owner_id = u.id WHERE u.created_at >= ?::timestamptz", window.toSeconds(), Schema.odt(since));
    return new Activation(r.get(0, Long.class), r.get(1, Long.class));
  }

  @Override
  public List<DayCount> clicksPerDay(LocalDate from, LocalDate to) {
    return dsl.fetch("SELECT d::date, COALESCE(r.n, 0) FROM generate_series(?::date, ?::date, interval '1 day') d "
            + "LEFT JOIN (SELECT day, sum(clicks) AS n FROM rollups_daily WHERE resource_type = 'link' AND dimension = 'total' AND day BETWEEN ?::date AND ?::date "
            + "GROUP BY day) r ON r.day = d::date ORDER BY 1",
        from, to, from, to).map(r -> new DayCount(r.get(0, LocalDate.class), r.get(1, Long.class)));
  }

  @Override
  public ResourceCounts resourceCounts() {
    var r = dsl.fetchOne("SELECT (SELECT count(*) FROM links), (SELECT count(*) FROM qr_codes), (SELECT count(*) FROM bio_pages)");
    return new ResourceCounts(r.get(0, Long.class), r.get(1, Long.class), r.get(2, Long.class));
  }

  @Override
  public long databaseBytes() {
    return dsl.fetchOne("SELECT pg_database_size(current_database())").get(0, Long.class);
  }
}
