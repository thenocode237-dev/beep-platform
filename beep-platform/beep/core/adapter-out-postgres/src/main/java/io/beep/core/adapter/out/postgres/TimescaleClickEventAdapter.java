package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.ClickEventRepository;
import io.beep.core.domain.analytics.ClickEvent;
import org.jooq.DSLContext;
import java.util.List;
/** Alternative behind the existing port; the compatibility view keeps rollups and erasure unchanged. */
public class TimescaleClickEventAdapter implements ClickEventRepository {
 private final PostgresClickEventAdapter delegate;
 public TimescaleClickEventAdapter(DSLContext db){if(!Boolean.TRUE.equals(db.fetchSingle("SELECT EXISTS(SELECT 1 FROM timescaledb_information.hypertables WHERE hypertable_name='click_events_timescale')").get(0,Boolean.class)))throw new IllegalStateException("Apply infra/timescale/enable.sql first");delegate=new PostgresClickEventAdapter(db,"click_events_timescale");}
 public void insertBatch(List<ClickEvent> events){delegate.insertBatch(events);}
}
