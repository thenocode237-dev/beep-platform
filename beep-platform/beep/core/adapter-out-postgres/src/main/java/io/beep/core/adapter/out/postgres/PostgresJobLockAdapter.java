package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.JobLockPort;
import java.sql.*;
import java.util.function.Supplier;
/** Dedicated direct PostgreSQL connection: session locks must never use transaction pooling. */
public class PostgresJobLockAdapter implements JobLockPort {
 private final String url,user,password;public PostgresJobLockAdapter(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
 public String run(String job,Supplier<String> work){try(var c=DriverManager.getConnection(url,user,password)){try(var stmt=c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?,5105))")){stmt.setString(1,job);try(var r=stmt.executeQuery()){r.next();if(!r.getBoolean(1))return "skipped: already running";}}try{return work.get();}finally{try(var stmt=c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,5105))")){stmt.setString(1,job);stmt.execute();}}}catch(SQLException e){throw new IllegalStateException("job lock unavailable",e);}}
}
