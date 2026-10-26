package io.beep.bo.adapter.out.postgres;
import io.beep.bo.app.port.ConsoleRepository;
import io.beep.bo.domain.DomainException;
import java.time.*;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class PostgresConsoleAdapter implements ConsoleRepository {
  private final DSLContext db;private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
  public PostgresConsoleAdapter(DSLContext db){this.db=db;}
  private List<Map<String,Object>> rows(String sql,Object... args){return db.fetch("SELECT to_jsonb(q) AS data FROM ("+sql+") q",args).map(r->{try{return json.readValue(r.get("data",JSONB.class).data(),new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}});}
  private static OffsetDateTime at(Instant t){return t.atOffset(ZoneOffset.UTC);}
  @Override public List<Map<String,Object>> drafts(){return rows("SELECT * FROM content_drafts ORDER BY updated_at DESC");}
  @Override public Map<String,Object> saveDraft(UUID staff,Map<String,Object> b,Instant now){var r=db.fetchOptional("INSERT INTO content_drafts(kind,slug,locale,title,body,updated_by,updated_at,version) VALUES(?,?,?,?,?,?,?::timestamptz,?) ON CONFLICT(kind,slug,locale) DO UPDATE SET title=excluded.title,body=excluded.body,status='draft',version=content_drafts.version+1,updated_by=excluded.updated_by,updated_at=excluded.updated_at WHERE content_drafts.version=? RETURNING id",b.get("kind"),b.get("slug"),b.get("locale"),b.get("title"),b.get("body"),staff,at(now),b.getOrDefault("initialVersion",1),b.getOrDefault("version",0));if(r.isEmpty())throw new DomainException("conflict");return draft(r.get().get("id",UUID.class));}
  @Override public Map<String,Object> draft(UUID id){return rows("SELECT * FROM content_drafts WHERE id=?",id).stream().findFirst().orElseThrow(()->new DomainException("not_found"));}
  @Override public void markPublished(UUID id,int expected,int published,String status,Instant now){if(db.execute("UPDATE content_drafts SET status=?,published_at=?::timestamptz,published_version=?,version=? WHERE id=? AND version=?",status,at(now),published,published,id,expected)==0)throw new DomainException("conflict");}
  @Override public List<Map<String,Object>> notes(UUID w){return rows("SELECT n.id,n.body,n.created_at,s.email FROM support_notes n JOIN staff_users s ON s.id=n.staff_id WHERE workspace_id=? ORDER BY created_at DESC LIMIT 100",w);}
  @Override public void note(UUID w,UUID staff,String body,Instant now){db.execute("INSERT INTO support_notes(workspace_id,staff_id,body,created_at) VALUES(?,?,?,?::timestamptz)",w,staff,body,at(now));}
  @Override public void impersonation(String hash,UUID staff,UUID w,Instant expires){db.execute("DELETE FROM readonly_impersonations WHERE expires_at<now() OR staff_id=?",staff);db.execute("INSERT INTO readonly_impersonations(token_hash,staff_id,workspace_id,expires_at) VALUES(?,?,?,?::timestamptz)",hash,staff,w,at(expires));}
  @Override public UUID impersonated(String hash,UUID staff,Instant now){return db.fetchOptional("SELECT workspace_id FROM readonly_impersonations WHERE token_hash=? AND staff_id=? AND expires_at>?::timestamptz",hash,staff,at(now)).map(r->r.get("workspace_id",UUID.class)).orElseThrow(()->new DomainException("not_found"));}
  @Override public void revokeImpersonation(String hash,UUID staff){db.execute("DELETE FROM readonly_impersonations WHERE token_hash=? AND staff_id=?",hash,staff);}
  @Override public List<Map<String,Object>> staff(){return rows("SELECT id,email,role,active,created_at FROM staff_users ORDER BY email");}
  @Override public List<UUID> workspaceReferences(UUID after,int limit){return db.fetch("SELECT workspace_id FROM (SELECT workspace_id FROM support_notes UNION SELECT workspace_id FROM readonly_impersonations) r WHERE (?::uuid IS NULL OR workspace_id>?) ORDER BY workspace_id LIMIT ?",after,after,limit).map(r->r.get("workspace_id",UUID.class));}
  @Override public void purgeWorkspace(UUID w){db.transaction(c->{c.dsl().execute("DELETE FROM support_notes WHERE workspace_id=?",w);c.dsl().execute("DELETE FROM readonly_impersonations WHERE workspace_id=?",w);});}
  @Override public void expireViews(Instant now){db.execute("DELETE FROM readonly_impersonations WHERE expires_at<=?::timestamptz",at(now));}
  @Override public void staffRole(UUID actor,UUID target,String role,boolean active){if(actor.equals(target))throw new DomainException("forbidden");db.transaction(c->{var d=c.dsl();d.execute("SELECT pg_advisory_xact_lock(426032)");var r=d.fetchOptional("SELECT role,active FROM staff_users WHERE id=? FOR UPDATE",target).orElseThrow(()->new DomainException("not_found"));if(r.get("role",String.class).equals("superadmin")&&r.get("active",Boolean.class)&&(!role.equals("superadmin")||!active)&&((Number)d.fetchValue("SELECT count(*) FROM staff_users WHERE role='superadmin' AND active")).longValue()<=1)throw new DomainException("forbidden");d.execute("UPDATE staff_users SET role=?,active=? WHERE id=?",role,active,target);d.execute("DELETE FROM staff_sessions WHERE staff_id=?",target);d.execute("DELETE FROM readonly_impersonations WHERE staff_id=?",target);});}
}
