package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.ClickEventRepository;
import io.beep.core.domain.analytics.ClickEvent;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Insertion multi-lignes dans la table partitionnée. Si un événement tombe dans un mois sans partition (worker en retard),
 * la partition est créée puis l'insertion rejouée une fois : aucun événement perdu pour cette raison.
 */
public class PostgresClickEventAdapter implements ClickEventRepository {
  private static final Table<Record> EVENTS = DSL.table(DSL.name("click_events"));
  private static final Field<?>[] COLUMNS = {
      f("id", SQLDataType.UUID), f("ts", SQLDataType.TIMESTAMPWITHTIMEZONE), f("workspace_id", SQLDataType.UUID), f("kind", SQLDataType.CLOB),
      f("link_id", SQLDataType.UUID), f("qr_id", SQLDataType.UUID), f("bio_page_id", SQLDataType.UUID), f("bio_block_id", SQLDataType.CLOB),
      f("visitor_hash", SQLDataType.CLOB), f("country", SQLDataType.CLOB), f("device", SQLDataType.CLOB), f("os", SQLDataType.CLOB),
      f("browser", SQLDataType.CLOB), f("lang", SQLDataType.CLOB), f("referrer", SQLDataType.CLOB), f("is_bot", SQLDataType.BOOLEAN)};

  private final DSLContext dsl;
  private final Table<Record> target;

  public PostgresClickEventAdapter(DSLContext dsl) {
    this(dsl,"click_events");
  }

  public PostgresClickEventAdapter(DSLContext dsl,String table){
    if(!java.util.Set.of("click_events","click_events_timescale").contains(table))throw new IllegalArgumentException("event table");
    this.target=DSL.table(DSL.name(table));
    this.dsl = dsl;
  }

  @Override
  public void insertBatch(List<ClickEvent> events) {
    if (events.isEmpty()) return;
    try {
      insert(events);
    } catch (RuntimeException e) {
      if (!Sql.hasState(e, Sql.NO_PARTITION)) throw e;
      Instant min = events.stream().map(ClickEvent::ts).min(Comparator.naturalOrder()).orElseThrow();
      Instant max = events.stream().map(ClickEvent::ts).max(Comparator.naturalOrder()).orElseThrow();
      YearMonth from = YearMonth.from(min.atZone(ZoneOffset.UTC));
      int months = (int) ChronoUnit.MONTHS.between(from, YearMonth.from(max.atZone(ZoneOffset.UTC)));
      dsl.execute("SELECT ensure_click_partitions(?::date, ?)", from.atDay(1), months);
      insert(events);
    }
  }

  private void insert(List<ClickEvent> events) {
    InsertValuesStepN<Record> insert = dsl.insertInto(target, COLUMNS);
    for (ClickEvent e : events) {
      insert = insert.values(e.id(), Schema.odt(e.ts()), e.workspaceId(), e.kind().db(), e.linkId(), e.qrId(), e.bioPageId(), e.bioBlockId(),
          e.visitorHash(), e.country(), e.device().db(), e.os(), e.browser(), e.lang(), e.referrer(), e.bot());
    }
    insert.execute();
  }

  private static <T> Field<T> f(String column, org.jooq.DataType<T> type) {
    return DSL.field(DSL.name(column), type);
  }
}
