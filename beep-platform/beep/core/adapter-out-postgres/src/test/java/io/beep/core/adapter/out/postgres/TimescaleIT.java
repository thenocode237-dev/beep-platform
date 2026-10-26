package io.beep.core.adapter.out.postgres;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.domain.analytics.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
@EnabledIfEnvironmentVariable(named="TEST_TIMESCALE_URL",matches=".+")
class TimescaleIT {
 @Test void alternativeStoreKeepsLegacyDataRollupsAndPrivacyCompatible()throws Exception{
  var pg=new PGSimpleDataSource();pg.setUrl(System.getenv("TEST_TIMESCALE_URL"));var f=Flyway.configure().dataSource(pg).cleanDisabled(false).load();f.clean();f.migrate();f.validate();var db=DSL.using(pg,SQLDialect.POSTGRES);UUID workspace=UUID.randomUUID(),link=UUID.randomUUID();Instant now=Instant.now();
  ClickEvent old=new ClickEvent(UUID.randomUUID(),now,workspace,EventKind.CLICK,link,null,null,null,"visitor","FR",DeviceType.DESKTOP,"Linux","Firefox","fr","",false);
  new PostgresClickEventAdapter(db).insertBatch(List.of(old));
  try(var conn=pg.getConnection();var statement=conn.createStatement()){statement.execute(Files.readString(Path.of("../../../infra/timescale/enable.sql")));}
  var store=new TimescaleClickEventAdapter(db);var next=new ClickEvent(UUID.randomUUID(),now.plusSeconds(1),workspace,EventKind.CLICK,link,null,null,null,"visitor2","FR",DeviceType.DESKTOP,"Linux","Firefox","fr","",false);store.insertBatch(List.of(next));assertThat(db.fetchValue("SELECT count(*) FROM click_events")).isEqualTo(2L);assertThat(db.fetchValue("SELECT count(*) FROM click_events_timescale")).isEqualTo(1L);
  var rollups=new PostgresRollupAdapter(db);LocalDate day=now.atOffset(ZoneOffset.UTC).toLocalDate();rollups.rollupDaily(day,day);assertThat(new PostgresAnalyticsAdapter(db).totals(io.beep.core.app.port.AnalyticsRepository.Resource.LINK,link,day,day).clicks()).isEqualTo(2);
  db.execute("DELETE FROM click_events WHERE workspace_id=?",workspace);assertThat(db.fetchValue("SELECT count(*) FROM click_events")).isEqualTo(0L);db.execute("INSERT INTO erased_workspaces(workspace_id) VALUES(?)",workspace);store.insertBatch(List.of(next));assertThat(db.fetchValue("SELECT count(*) FROM click_events")).isEqualTo(0L);
 }
}
