package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.TrustRepository;
import io.beep.core.domain.DomainException;
import java.time.*;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresTrustAdapter implements TrustRepository {
  private final DSLContext dsl;
  public PostgresTrustAdapter(DSLContext dsl) { this.dsl = dsl; }
  @Override public void lockSafety() {dsl.execute("SELECT pg_advisory_xact_lock(88211006)");}
  private static OffsetDateTime at(Instant at) { return at.atOffset(ZoneOffset.UTC); }
  private static Instant instant(Record r, String key) { var v = r.get(key, OffsetDateTime.class); return v == null ? null : v.toInstant(); }
  private static Report report(Record r) { return new Report(r.get("id", UUID.class), r.get("url", String.class), r.get("category", String.class), r.get("detail", String.class), r.get("source", String.class), r.get("status", String.class), r.get("reason", String.class), instant(r,"created_at"), instant(r,"resolved_at")); }
  @Override public void report(Report r) {
    dsl.execute("INSERT INTO abuse_reports(id,url,category,detail,source,status,created_at) VALUES (?,?,?,?,?,'open',?::timestamptz) ON CONFLICT DO NOTHING", r.id(),r.url(),r.category(),r.detail(),r.source(),at(r.createdAt()));
  }
  @Override public Reports reports(String status, int limit, int offset, Instant now) {
    var rows = dsl.fetch("SELECT * FROM abuse_reports WHERE (?::text IS NULL OR status = ?) ORDER BY created_at,id LIMIT ? OFFSET ?",status,status,limit,offset).map(PostgresTrustAdapter::report);
    var n = dsl.fetchSingle("SELECT count(*) FILTER(WHERE status='open') AS open, count(*) FILTER(WHERE status='open' AND created_at < ?::timestamptz) AS overdue FROM abuse_reports",at(now.minus(Duration.ofHours(24))));
    return new Reports(rows,n.get("open",Long.class),n.get("overdue",Long.class));
  }
  @Override public Report resolve(UUID id,String decision,String reason,String staff,Instant now) {
    var r = dsl.fetchOptional("UPDATE abuse_reports SET status=?,reason=?,resolved_by=?,resolved_at=?::timestamptz WHERE id=? AND status='open' RETURNING *",decision,reason,staff,at(now),id);
    return r.map(PostgresTrustAdapter::report).orElseThrow(() -> new DomainException("conflict"));
  }
  @Override public List<BlockedHost> blocklist() { return dsl.fetch("SELECT * FROM blocked_hosts ORDER BY host").map(r -> new BlockedHost(r.get("host",String.class),r.get("reason",String.class),instant(r,"created_at"))); }
  @Override public boolean blocked(String host) {
    return Boolean.TRUE.equals(dsl.fetchValue("SELECT EXISTS(SELECT 1 FROM blocked_hosts WHERE ? = host OR right(?,length(host)+1) = '.' || host)",host,host));
  }
  @Override public void block(String host,boolean blocked,String reason,String staff,Instant now) {
    lockSafety();
    if (blocked) dsl.execute("INSERT INTO blocked_hosts(host,reason,created_at,updated_by) VALUES (?,?,?::timestamptz,?) ON CONFLICT(host) DO UPDATE SET reason=excluded.reason,updated_by=excluded.updated_by",host,reason,at(now),staff);
    else dsl.execute("DELETE FROM blocked_hosts WHERE host=?",host);
    // Only our own moderation disable is reverted. Staff and quota disables remain in effect.
    if (blocked) dsl.execute("UPDATE links SET status='disabled',disabled_reason='safety:' || ?,updated_at=?::timestamptz WHERE status='active' AND EXISTS(SELECT 1 FROM beep_destination_hosts(destination,rules) h WHERE h = ? OR right(h,length(?)+1) = '.' || ?)",host,at(now),host,host,host);
    else dsl.execute("UPDATE links SET status=CASE WHEN quota_exceeded_at<=?::timestamptz-interval '30 days' THEN 'disabled' ELSE 'active' END,disabled_reason=CASE WHEN quota_exceeded_at<=?::timestamptz-interval '30 days' THEN 'quota:overdue' ELSE NULL END,updated_at=?::timestamptz WHERE disabled_reason=? AND NOT EXISTS(SELECT 1 FROM blocked_hosts b WHERE EXISTS(SELECT 1 FROM beep_destination_hosts(destination,rules) h WHERE h=b.host OR right(h,length(b.host)+1)='.'||b.host))",at(now),at(now),at(now),"safety:"+host);
  }
  private static Flag flag(Record r) { return new Flag(r.get("key",String.class),r.get("enabled",Boolean.class),r.get("rollout_percent",Integer.class),r.get("version",Integer.class),r.get("reason",String.class)); }
  @Override public List<Flag> flags() { return dsl.fetch("SELECT * FROM feature_flags ORDER BY key").map(PostgresTrustAdapter::flag); }
  @Override public Flag publishFlag(String key,boolean enabled,int rollout,int version,String reason,String staff,Instant now) {
    return dsl.fetchOptional("UPDATE feature_flags SET enabled=?,rollout_percent=?,version=version+1,reason=?,updated_by=?,updated_at=?::timestamptz WHERE key=? AND version=? RETURNING *",enabled,rollout,reason,staff,at(now),key,version).map(PostgresTrustAdapter::flag).orElseThrow(() -> new DomainException("conflict"));
  }
  @Override public List<ScanTarget> scanTargets(int limit) { return dsl.fetch("SELECT id,destination,updated_at,rules::text FROM links WHERE status='active' AND (safety_checked_at IS NULL OR safety_checked_at<updated_at OR safety_checked_at<now()-interval '1 day') ORDER BY safety_checked_at NULLS FIRST,updated_at LIMIT ?",limit).map(r -> {
    var rules=LinkRulesJson.read(r.get("rules",String.class));
    List<String> urls=new ArrayList<>();urls.add(r.get("destination",String.class));
    if(rules.onelink()!=null) {if(rules.onelink().ios()!=null) urls.add(rules.onelink().ios());if(rules.onelink().android()!=null) urls.add(rules.onelink().android());if(rules.onelink().desktop()!=null) urls.add(rules.onelink().desktop());}
    rules.routes().forEach(x -> urls.add(x.url()));rules.variants().forEach(x -> urls.add(x.url()));
    return new ScanTarget(r.get("id",UUID.class),r.get("destination",String.class),instant(r,"updated_at"),List.copyOf(new LinkedHashSet<>(urls)));
  }); }
  @Override public Optional<String> destinationOf(String url,String mainHost) {
    var uri=java.net.URI.create(url);String slug=uri.getPath()==null?"":uri.getPath().replaceFirst("^/","");
    if(slug.isBlank() || slug.contains("/")) return Optional.empty();
    return dsl.fetchOptional("SELECT l.destination FROM links l LEFT JOIN custom_domains d ON d.id=l.domain_id WHERE l.slug=? AND ((l.domain_id IS NULL AND ?=?) OR d.hostname=?)",slug,uri.getHost(),mainHost,uri.getHost()).map(r -> r.get(0,String.class));
  }
  @Override public boolean scanned(UUID id,String url,Instant updated,Instant now) { return dsl.execute("UPDATE links SET safety_checked_at=?::timestamptz WHERE id=? AND destination=? AND updated_at=?::timestamptz",at(now),id,url,at(updated)) == 1; }
  @Override public void audit(String actor,String action,String target,String reason) { dsl.execute("INSERT INTO trust_audit(actor,action,target,reason) VALUES (?,?,?,?)",actor,action,target,reason); }
}
