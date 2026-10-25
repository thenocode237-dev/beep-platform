package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.PrivacyRepository;
import io.beep.core.domain.DomainException;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class PostgresPrivacyAdapter implements PrivacyRepository {
  private final DSLContext dsl;
  public PostgresPrivacyAdapter(DSLContext dsl) {this.dsl=dsl;}
  @Override public Map<String,Object> export(UUID id) {
    var user=dsl.fetchOptional("SELECT jsonb_build_object('id',id,'email',email,'status',status,'locale',locale,'createdAt',created_at,'emailVerifiedAt',email_verified_at) AS data FROM users WHERE id=?",id).orElseThrow(() -> new DomainException("not_found"));
    Map<String,Object> out=new LinkedHashMap<>();out.put("formatVersion",1);out.put("user",Sql.map(user.get("data",JSONB.class)));
    var ws=dsl.fetchOptional("SELECT id FROM workspaces WHERE owner_id=?",id);
    if(ws.isEmpty()) return out;
    UUID wid=ws.get().get("id",UUID.class);
    out.put("workspace", rows("SELECT to_jsonb(w) AS data FROM workspaces w WHERE id=?",wid));
    out.put("links",rows("SELECT to_jsonb(l)-'password_hash' AS data FROM links l WHERE workspace_id=?",wid));
    out.put("qrCodes",rows("SELECT to_jsonb(q) AS data FROM qr_codes q WHERE workspace_id=?",wid));
    out.put("bioPages",rows("SELECT to_jsonb(p) || jsonb_build_object('blocks',(SELECT coalesce(jsonb_agg(to_jsonb(b) ORDER BY b.position),'[]'::jsonb) FROM bio_blocks b WHERE b.page_id=p.id)) AS data FROM bio_pages p WHERE workspace_id=?",wid));
    out.put("domains",rows("SELECT to_jsonb(d)-'verify_token' AS data FROM custom_domains d WHERE workspace_id=?",wid));
    out.put("subscription",rows("SELECT to_jsonb(s) AS data FROM subscriptions s WHERE workspace_id=?",wid));
    out.put("payments",rows("SELECT to_jsonb(p)-'provider_ref'-'recorded_by' AS data FROM payments p WHERE workspace_id=?",wid));
    out.put("invoices",rows("SELECT to_jsonb(i) AS data FROM invoices i WHERE workspace_id=?",wid));
    out.put("analytics",rows("SELECT to_jsonb(r) AS data FROM rollups_daily r WHERE workspace_id=?",wid));
    out.put("entitlementOverrides",rows("SELECT to_jsonb(e)-'updated_by' AS data FROM entitlement_overrides e WHERE workspace_id=?",wid));
    return out;
  }
  private List<Map<String,Object>> rows(String sql,UUID id) {return dsl.fetch(sql,id).map(r -> Sql.map(r.get("data",JSONB.class)));}
  @Override public void erase(UUID id) {
    var user=dsl.fetchOptional("SELECT email FROM users WHERE id=? FOR UPDATE",id).orElseThrow(() -> new DomainException("not_found"));
    var ws=dsl.fetchOptional("SELECT id FROM workspaces WHERE owner_id=? FOR UPDATE",id);
    if(ws.isPresent()) {
      UUID wid=ws.get().get("id",UUID.class);
      dsl.execute("SELECT pg_advisory_xact_lock(hashtextextended(?::text,17))",wid.toString());
      // A tombstone also rejects clicks buffered before erasure; no personal identifiers are stored.
      dsl.execute("INSERT INTO erased_workspaces(workspace_id) VALUES (?) ON CONFLICT DO NOTHING",wid);
      dsl.execute("INSERT INTO accounting_archive(number,invoice,payment) SELECT i.number,to_jsonb(i)-'workspace_id',to_jsonb(p)-'workspace_id' FROM invoices i JOIN payments p ON p.id=i.payment_id WHERE i.workspace_id=? ON CONFLICT DO NOTHING",wid);
      dsl.execute("DELETE FROM invoices WHERE workspace_id=?",wid);
      dsl.execute("DELETE FROM qr_codes WHERE workspace_id=?",wid);
      dsl.execute("DELETE FROM slugs WHERE target_id IN (SELECT id FROM links WHERE workspace_id=? UNION ALL SELECT id FROM bio_pages WHERE workspace_id=?)",wid,wid);
      dsl.execute("DELETE FROM click_events WHERE workspace_id=?",wid);
      dsl.execute("DELETE FROM rollups_hourly WHERE workspace_id=?",wid);
      dsl.execute("DELETE FROM rollups_daily WHERE workspace_id=?",wid);
    }
    dsl.execute("DELETE FROM beta_invitations WHERE email=?",user.get("email",String.class));
    dsl.execute("DELETE FROM users WHERE id=?",id);
  }
}
