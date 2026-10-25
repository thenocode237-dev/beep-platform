package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.QuotaLifecycleRepository;
import java.time.*;
import java.util.*;
import org.jooq.DSLContext;
public class PostgresQuotaLifecycleAdapter implements QuotaLifecycleRepository {
  private final DSLContext dsl;
  public PostgresQuotaLifecycleAdapter(DSLContext dsl) {this.dsl=dsl;}
  @Override public List<Notice> notices(Instant now) {
    return dsl.fetch("SELECT w.id,u.email,u.locale,count(*) AS n,min(l.quota_exceeded_at) AS expired FROM links l JOIN workspaces w ON w.id=l.workspace_id JOIN users u ON u.id=w.owner_id WHERE l.quota_exceeded_at IS NOT NULL AND l.quota_notified_at IS NULL GROUP BY w.id,u.email,u.locale").map(r -> new Notice(r.get("id",UUID.class),r.get("email",String.class),r.get("locale",String.class),r.get("n",Long.class),r.get("expired",OffsetDateTime.class).toInstant()));
  }
  @Override public void notified(UUID id,Instant now) {dsl.execute("UPDATE links SET quota_notified_at=?::timestamptz WHERE workspace_id=? AND quota_exceeded_at IS NOT NULL AND quota_notified_at IS NULL",now.atOffset(ZoneOffset.UTC),id);}
  @Override public String apply(Instant now) {
    var at=now.atOffset(ZoneOffset.UTC);
    // Same workspace lock as checkout/creation. A concurrent renewal cannot race deletion.
    dsl.fetch("SELECT id FROM workspaces ORDER BY id FOR UPDATE");
    dsl.execute("CREATE TEMP TABLE IF NOT EXISTS quota_excess(id uuid PRIMARY KEY,expired timestamptz) ON COMMIT DROP");
    dsl.execute("TRUNCATE quota_excess");
    dsl.execute("INSERT INTO quota_excess SELECT id,period_end FROM (SELECT l.id,s.period_end,row_number() OVER(PARTITION BY l.workspace_id ORDER BY l.created_at,l.id) AS rank,coalesce(o.value,(p.entitlements->>'links.stock_max')::bigint,-1) AS cap FROM links l JOIN workspaces w ON w.id=l.workspace_id JOIN plans p ON p.code=w.plan_code JOIN subscriptions s ON s.workspace_id=w.id LEFT JOIN entitlement_overrides o ON o.workspace_id=w.id AND o.key='links.stock_max' WHERE s.status='expired' AND w.plan_code='starter') ranked WHERE cap>=0 AND rank>cap");
    int restored=dsl.execute("UPDATE links SET quota_exceeded_at=NULL,quota_notified_at=NULL,status=CASE WHEN disabled_reason='quota:overdue' THEN 'active' ELSE status END,disabled_reason=CASE WHEN disabled_reason='quota:overdue' THEN NULL ELSE disabled_reason END WHERE quota_exceeded_at IS NOT NULL AND id NOT IN(SELECT id FROM quota_excess)");
    dsl.execute("UPDATE links l SET quota_exceeded_at=q.expired FROM quota_excess q WHERE l.id=q.id AND l.quota_exceeded_at IS NULL");
    int paused=dsl.execute("UPDATE links SET status='disabled',disabled_reason='quota:overdue',updated_at=?::timestamptz WHERE quota_exceeded_at<=?::timestamptz-interval '30 days' AND status='active'",at,at);
    // A successful warning must precede erasure by at least 7 days. A mail outage defers deletion.
    dsl.execute("CREATE TEMP TABLE quota_delete ON COMMIT DROP AS SELECT id FROM links WHERE quota_exceeded_at<=?::timestamptz-interval '90 days' AND quota_notified_at<=?::timestamptz-interval '7 days'",at,at);
    dsl.execute("DELETE FROM qr_codes WHERE link_id IN(SELECT id FROM quota_delete)");
    dsl.execute("DELETE FROM slugs WHERE kind='link' AND target_id IN(SELECT id FROM quota_delete)");
    dsl.execute("DELETE FROM click_events WHERE link_id IN(SELECT id FROM quota_delete)");
    dsl.execute("DELETE FROM rollups_hourly WHERE resource_type='link' AND resource_id IN(SELECT id FROM quota_delete)");
    dsl.execute("DELETE FROM rollups_daily WHERE resource_type='link' AND resource_id IN(SELECT id FROM quota_delete)");
    int deleted=dsl.execute("DELETE FROM links WHERE id IN(SELECT id FROM quota_delete)");
    return "restored="+restored+" paused="+paused+" deleted="+deleted;
  }
}
