package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.DeveloperRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.conversion.Attribution.Touch;
import java.time.*;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class PostgresDeveloperAdapter implements DeveloperRepository {
  private final DSLContext db;
  private static final ObjectMapper JSON=new ObjectMapper();
  public PostgresDeveloperAdapter(DSLContext db){this.db=db;}
  private static OffsetDateTime at(Instant t){return t==null?null:t.atOffset(ZoneOffset.UTC);}
  private static Instant time(Record r,String key){var t=r.get(key,OffsetDateTime.class);return t==null?null:t.toInstant();}
  private static Set<String> strings(Record r,String key){return Set.copyOf(Arrays.asList(r.get(key,String[].class)));}
  private static String json(Object v){try{return JSON.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException(e);}}
  private static Map<String,String> pixels(Record r){try{return JSON.readValue(r.get("pixels",String.class),new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
  private static ApiKey key(Record r){return new ApiKey(r.get("id",UUID.class),r.get("workspace_id",UUID.class),r.get("name",String.class),r.get("prefix",String.class),r.get("token_hash",String.class),strings(r,"scopes"),List.copyOf(strings(r,"origins")),r.get("public_key",Boolean.class),time(r,"revoked_at"),time(r,"created_at"));}
  @Override public void key(ApiKey k){db.execute("INSERT INTO api_keys VALUES(?,?,?,?,?,?::text[],?::text[],?,?::timestamptz,?::timestamptz)",k.id(),k.workspaceId(),k.name(),k.prefix(),k.hash(),k.scopes().toArray(String[]::new),k.origins().toArray(String[]::new),k.publicKey(),at(k.revokedAt()),at(k.createdAt()));}
  @Override public Optional<ApiKey> keyByHash(String h){return db.fetchOptional("SELECT * FROM api_keys WHERE token_hash=? AND revoked_at IS NULL",h).map(PostgresDeveloperAdapter::key);}
  @Override public List<ApiKey> keys(UUID ws){return db.fetch("SELECT * FROM api_keys WHERE workspace_id=? ORDER BY revoked_at NULLS FIRST,created_at DESC LIMIT 100",ws).map(PostgresDeveloperAdapter::key);}
  @Override public void revoke(UUID ws,UUID id,Instant t){if(db.execute("UPDATE api_keys SET revoked_at=?::timestamptz WHERE workspace_id=? AND id=?",at(t),ws,id)==0)throw new DomainException("not_found");}
  @Override public void lock(UUID ws){db.execute("SELECT id FROM workspaces WHERE id=? FOR UPDATE",ws);}
  @Override public boolean rate(UUID ws,String bucket,long limit,Instant t){if(limit<=0)return false;return db.fetchOptional("INSERT INTO api_buckets VALUES(?,?,date_trunc('minute',?::timestamptz),1) ON CONFLICT(workspace_id,bucket,minute) DO UPDATE SET count=api_buckets.count+1 WHERE api_buckets.count<? RETURNING count",ws,bucket,at(t),limit).isPresent();}
  @Override public Optional<Reply> reply(UUID ws,String key,Instant t){return db.fetchOptional("SELECT * FROM api_idempotency WHERE workspace_id=? AND key=? AND expires_at>?::timestamptz",ws,key,at(t)).map(r->new Reply(r.get("fingerprint",String.class),r.get("status",Integer.class),r.get("content_type",String.class),r.get("body",String.class)));}
  @Override public void reply(UUID ws,String key,Reply r,Instant t){db.execute("INSERT INTO api_idempotency VALUES(?,?,?,?,?,?,?::timestamptz) ON CONFLICT(workspace_id,key) DO UPDATE SET fingerprint=excluded.fingerprint,status=excluded.status,content_type=excluded.content_type,body=excluded.body,expires_at=excluded.expires_at",ws,key,r.fingerprint(),r.status(),r.contentType(),r.body(),at(t.plus(Duration.ofHours(24))));}
  private static Tracking tracking(Record r){return new Tracking(r.get("link_id",UUID.class),r.get("workspace_id",UUID.class),r.get("enabled",Boolean.class),r.get("model",String.class),r.get("window_days",Integer.class),pixels(r));}
  @Override public Optional<Tracking> tracking(UUID id){return db.fetchOptional("SELECT * FROM conversion_links WHERE link_id=?",id).map(PostgresDeveloperAdapter::tracking);}
  @Override public List<Tracking> trackingFor(UUID ws){return db.fetch("SELECT * FROM conversion_links WHERE workspace_id=? ORDER BY link_id",ws).map(PostgresDeveloperAdapter::tracking);}
  @Override public void tracking(Tracking c){db.execute("INSERT INTO conversion_links VALUES(?,?,?,?,?,?::jsonb) ON CONFLICT(link_id) DO UPDATE SET enabled=excluded.enabled,model=excluded.model,window_days=excluded.window_days,pixels=excluded.pixels",c.linkId(),c.workspaceId(),c.enabled(),c.model(),c.windowDays(),json(c.pixels()));}
  @Override public void touch(UUID ws,String cid,UUID link,String visitor,Instant t){db.execute("INSERT INTO conversion_touches VALUES(?,?,?,?,?::timestamptz) ON CONFLICT(cid) DO NOTHING",cid,ws,link,visitor,at(t));}
  @Override public void identify(UUID ws,String cid,String visitor){db.execute("UPDATE conversion_touches SET visitor=? WHERE workspace_id=? AND cid=? AND (visitor IS NULL OR visitor=?)",visitor,ws,cid,visitor);}
  @Override public List<Touch> touches(UUID ws,String visitor,Instant since){return db.fetch("SELECT cid,link_id,at FROM conversion_touches WHERE workspace_id=? AND visitor=? AND at>=?::timestamptz ORDER BY at,cid LIMIT 10000",ws,visitor,at(since)).map(r->new Touch(r.get("cid",String.class),r.get("link_id",UUID.class),time(r,"at")));}
  private static Event event(Record r){return new Event(r.get("id",UUID.class),r.get("workspace_id",UUID.class),r.get("name",String.class),r.get("visitor",String.class),r.get("cid",String.class),r.get("link_id",UUID.class),r.get("value",java.math.BigDecimal.class),r.get("currency",String.class),r.get("page",String.class),time(r,"at"),utm(r));}
  private static Map<String,String> utm(Record r){try{return JSON.readValue(r.get("utm",String.class),new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
  @Override public boolean event(Event e,long max){
    lock(e.workspaceId());
    if(db.fetchExists(org.jooq.impl.DSL.selectOne().from("conversion_events").where("workspace_id=? AND id=?",e.workspaceId(),e.id())))return false;
    var period=db.fetchValue("SELECT beep_monthly_period(?,?::timestamptz)",e.workspaceId(),at(e.at()));
    if(max==0 || db.fetchOptional("INSERT INTO conversion_usage VALUES(?,?::timestamptz,1) ON CONFLICT(workspace_id,period) DO UPDATE SET count=conversion_usage.count+1 WHERE ? < 0 OR conversion_usage.count < ? RETURNING count",e.workspaceId(),period,max,max).isEmpty())throw new DomainException("quota_exceeded");
    db.execute("INSERT INTO conversion_events VALUES(?,?,?,?,?,?,?,?,?,?::timestamptz,?::jsonb)",e.id(),e.workspaceId(),e.name(),e.visitor(),e.cid(),e.linkId(),e.value(),e.currency(),e.page(),at(e.at()),json(e.utm()));return true;
  }
  private static List<Map<String,Object>> maps(org.jooq.Result<? extends Record> rows){return rows.stream().map(r->(Map<String,Object>)new LinkedHashMap<String,Object>(r.intoMap())).toList();}
  @Override public Map<String,Object> analytics(UUID ws,Instant since){
    var out=new LinkedHashMap<String,Object>();
    out.put("funnel",maps(db.fetch("SELECT l.id AS \"linkId\",l.slug,COALESCE((SELECT sum(events) FROM conversion_daily c WHERE c.workspace_id=l.workspace_id AND c.dimension='click' AND c.key=l.id::text AND c.day>=?::timestamptz::date),0) AS clicks,COALESCE(sum(d.leads),0) AS leads,COALESCE(sum(d.sales),0) AS sales FROM links l LEFT JOIN conversion_daily d ON d.workspace_id=l.workspace_id AND d.dimension='link' AND d.key=l.id::text AND d.day>=?::timestamptz::date WHERE l.workspace_id=? GROUP BY l.id ORDER BY l.created_at DESC LIMIT 500",at(since),at(since),ws)));
    out.put("revenue",maps(db.fetch("SELECT NULLIF(key,'')::uuid AS \"linkId\",currency,sum(revenue) AS revenue FROM conversion_daily WHERE workspace_id=? AND dimension='link' AND day>=?::timestamptz::date AND sales>0 GROUP BY key,currency",ws,at(since))));
    out.put("pages",maps(db.fetch("SELECT key AS page,sum(events) AS views,sum(visitors) AS visitors FROM conversion_daily WHERE workspace_id=? AND dimension='page' AND day>=?::timestamptz::date GROUP BY key ORDER BY views DESC LIMIT 100",ws,at(since))));
    out.put("usage",maps(db.fetch("SELECT period,count FROM conversion_usage WHERE workspace_id=? ORDER BY period DESC LIMIT 1",ws)));
    out.put("campaigns",maps(db.fetch("SELECT key::jsonb->>'source' AS source,key::jsonb->>'campaign' AS campaign,NULLIF(currency,'') AS currency,sum(events) AS events,sum(revenue) AS revenue FROM conversion_daily WHERE workspace_id=? AND dimension='campaign' AND day>=?::timestamptz::date GROUP BY key,currency ORDER BY events DESC LIMIT 100",ws,at(since))));
    return out;
  }
  @Override public List<Event> events(UUID ws,Instant after,UUID afterId,int limit){return db.fetch("SELECT * FROM conversion_events WHERE workspace_id=? AND (at,id)>(?::timestamptz,?::uuid) ORDER BY at,id LIMIT ?",ws,at(after),afterId==null?new UUID(0,0):afterId,limit).map(PostgresDeveloperAdapter::event);}
  @Override public List<Event> visitor(UUID ws,String visitor){return db.fetch("SELECT * FROM conversion_events WHERE workspace_id=? AND visitor=? ORDER BY at,id",ws,visitor).map(PostgresDeveloperAdapter::event);}
  @Override public void eraseVisitor(UUID ws,String visitor){db.execute("DELETE FROM webhook_deliveries WHERE workspace_id=? AND payload::jsonb->'data'->>'visitor'=?",ws,visitor);db.execute("DELETE FROM conversion_events WHERE workspace_id=? AND visitor=?",ws,visitor);db.execute("DELETE FROM conversion_touches WHERE workspace_id=? AND visitor=?",ws,visitor);}
  private static Endpoint endpoint(Record r){return new Endpoint(r.get("id",UUID.class),r.get("workspace_id",UUID.class),r.get("url",String.class),strings(r,"events"),r.get("secret_cipher",String.class),r.get("enabled",Boolean.class),time(r,"created_at"),r.get("provider",String.class),r.get("config_cipher",String.class));}
  @Override public void endpoint(Endpoint e){db.execute("INSERT INTO webhook_endpoints VALUES(?,?,?,?::text[],?,?,?::timestamptz,?,?)",e.id(),e.workspaceId(),e.url(),e.events().toArray(String[]::new),e.secretCipher(),e.enabled(),at(e.createdAt()),e.provider(),e.configCipher());}
  @Override public List<Endpoint> endpoints(UUID ws){return db.fetch("SELECT * FROM webhook_endpoints WHERE workspace_id=? ORDER BY created_at DESC",ws).map(PostgresDeveloperAdapter::endpoint);}
  @Override public void disableEndpoint(UUID ws,UUID id){if(db.execute("UPDATE webhook_endpoints SET enabled=false WHERE workspace_id=? AND id=?",ws,id)==0)throw new DomainException("not_found");db.execute("UPDATE webhook_deliveries SET status='cancelled' WHERE workspace_id=? AND endpoint_id=? AND status IN ('pending','sending')",ws,id);}
  private static Delivery delivery(Record r){return new Delivery(r.get("id",UUID.class),r.get("workspace_id",UUID.class),r.get("endpoint_id",UUID.class),r.get("event",String.class),r.get("payload",String.class),r.get("attempts",Integer.class),r.get("status",String.class),r.get("http_status",Integer.class),time(r,"next_at"),time(r,"created_at"));}
  @Override public List<Delivery> deliveries(UUID ws){return db.fetch("SELECT * FROM webhook_deliveries WHERE workspace_id=? ORDER BY created_at DESC,id DESC LIMIT 100",ws).map(PostgresDeveloperAdapter::delivery);}
  @Override public void replay(UUID ws,UUID id,Instant now){if(db.execute("UPDATE webhook_deliveries d SET status='pending',attempts=0,next_at=?::timestamptz WHERE workspace_id=? AND id=? AND status IN ('failed','delivered') AND EXISTS(SELECT 1 FROM webhook_endpoints e WHERE e.id=d.endpoint_id AND e.enabled)",at(now),ws,id)==0)throw new DomainException("conflict");}
  @Override public List<Delivery> claim(Instant now,int limit){return db.fetch("WITH due AS (SELECT d.id FROM webhook_deliveries d JOIN webhook_endpoints e ON e.id=d.endpoint_id AND e.enabled WHERE d.status IN ('pending','sending') AND d.next_at<=?::timestamptz ORDER BY d.next_at FOR UPDATE OF d SKIP LOCKED LIMIT ?) UPDATE webhook_deliveries d SET status='sending',attempts=attempts+1,next_at=?::timestamptz FROM due WHERE d.id=due.id RETURNING d.*",at(now),limit,at(now.plusSeconds(120))).map(PostgresDeveloperAdapter::delivery);}
  @Override public void delivered(UUID id,int attempt,Integer status,Instant next,boolean terminal,Instant now){boolean ok=status!=null&&status>=200&&status<300;db.execute("UPDATE webhook_deliveries SET status=?,http_status=?,next_at=?::timestamptz,finished_at=?::timestamptz WHERE id=? AND status='sending' AND attempts=?",ok?"delivered":terminal?"failed":"pending",status,at(next),ok||terminal?at(now):null,id,attempt);}
  @Override public Map<String,Object> monitor(){var m=new LinkedHashMap<String,Object>();m.put("events",db.fetchValue("SELECT count(*) FROM conversion_events WHERE at>now()-interval '1 minute'"));m.put("rejections",maps(db.fetch("SELECT reason,sum(count) AS count FROM pipeline_rejections WHERE minute>now()-interval '24 hours' GROUP BY reason")));m.put("api",maps(db.fetch("SELECT bucket,sum(count) AS count FROM api_buckets WHERE bucket LIKE 'status:%' AND minute>now()-interval '24 hours' GROUP BY bucket")));m.put("deliveries",maps(db.fetch("SELECT status,count(*),greatest(0,extract(epoch FROM now()-min(created_at)))::bigint AS \"lagSeconds\" FROM webhook_deliveries GROUP BY status")));return m;}
  @Override public void apiMetric(UUID ws,int status,Instant now){rate(ws,"status:"+status,Long.MAX_VALUE,now);}
  @Override public void rejection(String reason,Instant now){db.execute("INSERT INTO pipeline_rejections VALUES(date_trunc('minute',?::timestamptz),?,1) ON CONFLICT(minute,reason) DO UPDATE SET count=pipeline_rejections.count+1",at(now),reason);}
  @Override public void retention(Instant now){
    db.execute("SELECT beep_conversion_retention(?::timestamptz)",at(now));
    db.execute("DELETE FROM conversion_events WHERE at<?::timestamptz",at(now.atZone(ZoneOffset.UTC).minusMonths(13).toInstant()));db.execute("DELETE FROM conversion_touches WHERE at<?::timestamptz",at(now.minus(Duration.ofDays(90))));
    db.execute("DELETE FROM api_idempotency WHERE expires_at<?::timestamptz",at(now));db.execute("DELETE FROM api_buckets WHERE minute<?::timestamptz",at(now.minus(Duration.ofDays(30))));db.execute("DELETE FROM pipeline_rejections WHERE minute<?::timestamptz",at(now.minus(Duration.ofDays(30))));db.execute("DELETE FROM webhook_deliveries WHERE created_at<?::timestamptz",at(now.minus(Duration.ofDays(30))));
  }
}
