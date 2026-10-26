package io.beep.bo.adapter.out.postgres;
import io.beep.bo.app.port.EnterpriseConsoleRepository;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import java.time.*;
import java.util.*;
public class PostgresEnterpriseConsoleAdapter implements EnterpriseConsoleRepository {
 private final DSLContext db;public PostgresEnterpriseConsoleAdapter(DSLContext db){this.db=db;}
 public void review(UUID staff,String decision,String reason,Instant now){db.execute("INSERT INTO staff_access_reviews SELECT ?,?,coalesce(jsonb_agg(jsonb_build_object('id',id,'role',role,'active',active)),'[]'::jsonb),?,?,?::timestamptz,?::timestamptz FROM staff_users",UUID.randomUUID(),staff,decision,reason,now.atOffset(ZoneOffset.UTC),now.plus(Duration.ofDays(90)).atOffset(ZoneOffset.UTC));}
 public List<Map<String,Object>> reviews(){return db.fetch("SELECT to_jsonb(r) AS data FROM (SELECT *,due_at<=now() AS overdue FROM staff_access_reviews ORDER BY reviewed_at DESC LIMIT 100) r").map(r->{try{return new com.fasterxml.jackson.databind.ObjectMapper().readValue(r.get("data",JSONB.class).data(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}});}
}
