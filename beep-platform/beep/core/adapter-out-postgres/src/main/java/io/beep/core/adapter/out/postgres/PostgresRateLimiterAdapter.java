package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.RateLimiterPort;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.jooq.DSLContext;
/** Atomic shared fixed-window limiter. Database time avoids skew between API nodes. */
public class PostgresRateLimiterAdapter implements RateLimiterPort {
 private final DSLContext db;public PostgresRateLimiterAdapter(DSLContext db){this.db=db;}
 private static String hash(String key){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @Override public boolean tryAcquire(String key,int max,Duration window){if(max<1||window.isZero()||window.isNegative())return false;var r=db.fetchSingle("INSERT INTO shared_rate_limits(key,hits,expires_at) VALUES(?,1,clock_timestamp()+(? * interval '1 millisecond')) ON CONFLICT(key) DO UPDATE SET hits=CASE WHEN shared_rate_limits.expires_at<=clock_timestamp() THEN 1 ELSE least(shared_rate_limits.hits+1,?+1) END,expires_at=CASE WHEN shared_rate_limits.expires_at<=clock_timestamp() THEN clock_timestamp()+(? * interval '1 millisecond') ELSE shared_rate_limits.expires_at END RETURNING hits",hash(key),window.toMillis(),max,window.toMillis());return r.get(0,Long.class)<=max;}
 @Override public void reset(String key){db.execute("DELETE FROM shared_rate_limits WHERE key=?",hash(key));}
}
