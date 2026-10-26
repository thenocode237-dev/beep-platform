package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.AnalyticsRepository;
import com.zaxxer.hikari.*;
import org.jooq.*;
import org.jooq.impl.DSL;
import java.time.*;
import java.util.*;
import java.util.function.Function;
/** Analytics can tolerate replication lag; authorization and redirects always use primary. */
public class ReplicaAnalyticsAdapter implements AnalyticsRepository,AutoCloseable {
 private final AnalyticsRepository primary,replica;private final HikariDataSource pool;private final DSLContext read;
 public ReplicaAnalyticsAdapter(DSLContext primary,String url,String user,String password){this.primary=new PostgresAnalyticsAdapter(primary);var cfg=new HikariConfig();cfg.setJdbcUrl(url);cfg.setUsername(user);cfg.setPassword(password);cfg.setMaximumPoolSize(5);cfg.setMinimumIdle(0);cfg.setReadOnly(true);cfg.setConnectionTimeout(2000);cfg.setInitializationFailTimeout(-1);cfg.setConnectionInitSql("SET statement_timeout='3s'");pool=new HikariDataSource(cfg);read=DSL.using(pool,SQLDialect.POSTGRES);replica=new PostgresAnalyticsAdapter(read);}
 private <T>T query(Function<AnalyticsRepository,T> op){try{return op.apply(replica);}catch(RuntimeException e){return op.apply(primary);}}
 public Totals totals(Resource r,UUID id,LocalDate f,LocalDate t){return query(a->a.totals(r,id,f,t));}
 public List<Point> daily(Resource r,UUID id,LocalDate f,LocalDate t){return query(a->a.daily(r,id,f,t));}
 public List<Point> hourly(Resource r,UUID id,Instant f,Instant t){return query(a->a.hourly(r,id,f,t));}
 public List<Breakdown> breakdown(Resource r,UUID id,String d,LocalDate f,LocalDate t,int top){return query(a->a.breakdown(r,id,d,f,t,top));}
 public Map<UUID,Long> totalsByResource(Resource r,Collection<UUID> ids,LocalDate f,LocalDate t){return query(a->a.totalsByResource(r,ids,f,t));}
 public List<Point> workspaceDaily(UUID id,LocalDate f,LocalDate t){return query(a->a.workspaceDaily(id,f,t));}
 public void close(){pool.close();}
}
