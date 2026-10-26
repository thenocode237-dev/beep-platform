package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.RollupRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;

/**
 * Agrégats et maintenance des événements. Chaque recalcul supprime puis réinsère la fenêtre dans une transaction :
 * idempotent, rattrape les écritures tardives, et ne double jamais un compte. Heures et jours en UTC, quel que soit le fuseau de session.
 */
public class PostgresRollupAdapter implements RollupRepository {
  private static final Pattern PARTITION = Pattern.compile("^click_events_(\\d{4})_(\\d{2})$");

  /**
   * Dimensions par événement humain : chaque ressource touchée (lien, QR, page bio en vue ou en clic de bloc) × chaque dimension.
   * Une clé NULL n'est pas agrégée (ex. langue inconnue, bloc hors page bio) ; un pays inconnu devient « ZZ ».
   */
  private static final String SOURCE = """
      FROM click_events e
      CROSS JOIN LATERAL (VALUES ('link', e.link_id), ('qr', e.qr_id),
                                 ('bio', CASE WHEN e.kind = 'view' THEN e.bio_page_id END),
                                 ('bio_click', CASE WHEN e.kind = 'bio_click' THEN e.bio_page_id END)) AS r(rt, rid)
      CROSS JOIN LATERAL (VALUES ('total', ''), ('country', COALESCE(e.country, 'ZZ')), ('device', e.device), ('os', e.os),
                                 ('browser', e.browser), ('referrer', e.referrer), ('lang', e.lang), ('source', e.kind),
                                 ('block', e.bio_block_id)) AS d(dim, k)
      WHERE e.ts >= ?::timestamptz AND e.ts < ?::timestamptz AND NOT e.is_bot AND r.rid IS NOT NULL AND d.k IS NOT NULL
      """;

  /** Rétention effective par workspace : override, sinon valeur du plan (-1 = illimitée). */
  private static final String RETENTION = """
      WITH ws AS (
        SELECT w.id, COALESCE((SELECT retention_days FROM enterprise_contracts c WHERE c.workspace_id=w.id AND c.status='active' AND c.starts_at<=now() AND c.ends_at>now()), o.value, (p.entitlements ->> 'analytics.retention_days')::bigint, 30) AS days
        FROM workspaces w
        JOIN plans p ON p.code = w.plan_code
        LEFT JOIN entitlement_overrides o ON o.workspace_id = w.id AND o.key = 'analytics.retention_days'
      )
      """;

  private final DSLContext dsl;

  public PostgresRollupAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public int rollupHourly(Instant from, Instant to) {
    return dsl.transactionResult(cfg -> {
      DSLContext tx = cfg.dsl();
      tx.execute("DELETE FROM rollups_hourly WHERE bucket >= ?::timestamptz AND bucket < ?::timestamptz", Schema.odt(from), Schema.odt(to));
      return tx.execute("INSERT INTO rollups_hourly (resource_type, resource_id, workspace_id, bucket, dimension, key, clicks, uniques) "
          + "SELECT r.rt, r.rid, e.workspace_id, date_trunc('hour', e.ts AT TIME ZONE 'UTC') AT TIME ZONE 'UTC', d.dim, d.k, count(*), count(DISTINCT e.visitor_hash) "
          + SOURCE + " GROUP BY 1, 2, 3, 4, 5, 6", Schema.odt(from), Schema.odt(to));
    });
  }

  @Override
  public int rollupDaily(LocalDate from, LocalDate to) {
    Instant lo = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    Instant hi = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    return dsl.transactionResult(cfg -> {
      DSLContext tx = cfg.dsl();
      tx.execute("DELETE FROM rollups_daily WHERE day >= ?::date AND day <= ?::date", from, to);
      return tx.execute("INSERT INTO rollups_daily (resource_type, resource_id, workspace_id, day, dimension, key, clicks, uniques) "
          + "SELECT r.rt, r.rid, e.workspace_id, (e.ts AT TIME ZONE 'UTC')::date, d.dim, d.k, count(*), count(DISTINCT e.visitor_hash) "
          + SOURCE + " GROUP BY 1, 2, 3, 4, 5, 6", Schema.odt(lo), Schema.odt(hi));
    });
  }

  @Override
  public void ensurePartitions(YearMonth from, int monthsAhead) {
    dsl.execute("SELECT ensure_click_partitions(?::date, ?)", from.atDay(1), monthsAhead);
  }

  @Override
  public List<String> dropPartitionsBefore(YearMonth keepFrom) {
    var oldest=dsl.fetchSingle("SELECT min((now()-make_interval(days=>retention_days))::date) FROM enterprise_contracts WHERE status='active' AND starts_at<=now() AND ends_at>now()").get(0,java.time.LocalDate.class);
    if(oldest!=null&&YearMonth.from(oldest).isBefore(keepFrom))keepFrom=YearMonth.from(oldest);
    List<String> dropped = new ArrayList<>();
    var names = dsl.fetch("SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid JOIN pg_class p ON p.oid = i.inhparent "
        + "WHERE p.relname = 'click_events' ORDER BY 1").map(r -> r.get(0, String.class));
    for (String name : names) {
      Matcher m = PARTITION.matcher(name);
      if (!m.matches()) continue; // seules nos partitions mensuelles, jamais un nom inattendu
      if (YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).isBefore(keepFrom)) {
        dsl.execute("DROP TABLE " + name);
        dropped.add(name);
      }
    }
    return dropped;
  }

  @Override
  public long purgeExpiredEvents(Instant now, int batchSize) {
    long total = 0;
    for (int i = 0; i < 200; i++) { // borne par exécution : le job horaire reprendra la suite
      int n = dsl.execute(RETENTION + """
          , doomed AS (
            SELECT e.ts, e.id FROM click_events e JOIN ws ON ws.id = e.workspace_id
            WHERE ws.days >= 0 AND e.ts < ?::timestamptz - make_interval(days => ws.days::int)
            LIMIT ?
          )
          DELETE FROM click_events e USING doomed d WHERE e.ts = d.ts AND e.id = d.id
          """, Schema.odt(now), batchSize);
      total += n;
      if (n < batchSize) break;
    }
    return total;
  }

  @Override
  public long purgeExpiredRollups(Instant now) {
    int daily = dsl.execute(RETENTION + "DELETE FROM rollups_daily r USING ws WHERE r.workspace_id = ws.id AND ws.days >= 0 "
        + "AND r.day < (?::timestamptz AT TIME ZONE 'UTC')::date - ws.days::int", Schema.odt(now));
    int hourly = dsl.execute(RETENTION + "DELETE FROM rollups_hourly r USING ws WHERE r.workspace_id = ws.id AND ws.days >= 0 "
        + "AND r.bucket < ?::timestamptz - make_interval(days => ws.days::int)", Schema.odt(now));
    return (long) daily + hourly;
  }

  static String partitionName(YearMonth m) {
    return "click_events_" + m.format(DateTimeFormatter.ofPattern("yyyy_MM"));
  }
}
