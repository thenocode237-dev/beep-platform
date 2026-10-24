package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.AnalyticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;

/** Lecture des agrégats. Séries complétées par generate_series (jours ou heures sans trafic = 0). */
public class PostgresAnalyticsAdapter implements AnalyticsRepository {
  private final DSLContext dsl;

  public PostgresAnalyticsAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Totals totals(Resource resource, UUID id, LocalDate from, LocalDate to) {
    var r = dsl.fetchOne("SELECT COALESCE(sum(clicks), 0), COALESCE(sum(uniques), 0) FROM rollups_daily "
        + "WHERE resource_type = ? AND resource_id = ? AND dimension = 'total' AND day BETWEEN ?::date AND ?::date", resource.db(), id, from, to);
    return new Totals(r.get(0, Long.class), r.get(1, Long.class));
  }

  @Override
  public List<Point> daily(Resource resource, UUID id, LocalDate from, LocalDate to) {
    return dsl.fetch("SELECT d::date, COALESCE(r.clicks, 0), COALESCE(r.uniques, 0) FROM generate_series(?::date, ?::date, interval '1 day') d "
            + "LEFT JOIN rollups_daily r ON r.day = d::date AND r.resource_type = ? AND r.resource_id = ? AND r.dimension = 'total' ORDER BY 1",
        from, to, resource.db(), id).map(r -> new Point(r.get(0, LocalDate.class).toString(), r.get(1, Long.class), r.get(2, Long.class)));
  }

  @Override
  public List<Point> hourly(Resource resource, UUID id, Instant from, Instant to) {
    return dsl.fetch("SELECT h, COALESCE(r.clicks, 0), COALESCE(r.uniques, 0) "
            + "FROM generate_series(?::timestamptz, ?::timestamptz - interval '1 hour', interval '1 hour') h "
            + "LEFT JOIN rollups_hourly r ON r.bucket = h AND r.resource_type = ? AND r.resource_id = ? AND r.dimension = 'total' ORDER BY 1",
        Schema.odt(from), Schema.odt(to), resource.db(), id)
        .map(r -> new Point(r.get(0, OffsetDateTime.class).toInstant().toString(), r.get(1, Long.class), r.get(2, Long.class)));
  }

  @Override
  public List<Breakdown> breakdown(Resource resource, UUID id, String dimension, LocalDate from, LocalDate to, int top) {
    return dsl.fetch("SELECT key, sum(clicks) AS c FROM rollups_daily WHERE resource_type = ? AND resource_id = ? AND dimension = ? AND day BETWEEN ?::date AND ?::date "
        + "GROUP BY key ORDER BY c DESC, key LIMIT ?", resource.db(), id, dimension, from, to, top)
        .map(r -> new Breakdown(r.get(0, String.class), r.get(1, Long.class)));
  }

  @Override
  public Map<UUID, Long> totalsByResource(Resource resource, Collection<UUID> ids, LocalDate from, LocalDate to) {
    Map<UUID, Long> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    dsl.fetch("SELECT resource_id, sum(clicks) FROM rollups_daily WHERE resource_type = ? AND resource_id = ANY(?::uuid[]) AND dimension = 'total' "
            + "AND day BETWEEN ?::date AND ?::date GROUP BY resource_id", resource.db(), ids.toArray(new UUID[0]), from, to)
        .forEach(r -> out.put(r.get(0, UUID.class), r.get(1, Long.class)));
    return out;
  }

  @Override
  public List<Point> workspaceDaily(UUID workspaceId, LocalDate from, LocalDate to) {
    return dsl.fetch("SELECT d::date, COALESCE(r.c, 0), COALESCE(r.u, 0) FROM generate_series(?::date, ?::date, interval '1 day') d "
            + "LEFT JOIN (SELECT day, sum(clicks) AS c, sum(uniques) AS u FROM rollups_daily WHERE workspace_id = ? AND resource_type = 'link' "
            + "AND dimension = 'total' AND day BETWEEN ?::date AND ?::date GROUP BY day) r ON r.day = d::date ORDER BY 1",
        from, to, workspaceId, from, to).map(r -> new Point(r.get(0, LocalDate.class).toString(), r.get(1, Long.class), r.get(2, Long.class)));
  }
}
