package io.beep.core.adapter.out.postgres;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.beep.core.app.port.DomainRouterPort;
import java.time.Duration;
import java.util.Locale;

/** Cache mémoire du routage par hôte : les hôtes inconnus sont mis en cache 30 s (balayage de noms), les domaines connus 10 min, invalidés par `domain:<hôte>`. */
public class CachingDomainRouterAdapter implements DomainRouterPort {
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);

  private final DomainRouterPort delegate;
  private final Cache<String, Route> routes;

  public CachingDomainRouterAdapter(DomainRouterPort delegate, long maxEntries) {
    this.delegate = delegate;
    this.routes = Caffeine.newBuilder().maximumSize(Math.max(1000, maxEntries / 10)).recordStats().expireAfter(new Expiry<String, Route>() {
      public long expireAfterCreate(String k, Route v, long now) {
        return (v.kind() == Route.Kind.UNKNOWN ? NEGATIVE_TTL : TTL).toNanos();
      }

      public long expireAfterUpdate(String k, Route v, long now, long current) {
        return expireAfterCreate(k, v, now);
      }

      public long expireAfterRead(String k, Route v, long now, long current) {
        return current;
      }
    }).build();
  }

  @Override
  public Route route(String host) {
    String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
    return routes.get(h, delegate::route);
  }

  public void invalidate(String host) {
    routes.invalidate(host);
  }

  public void invalidateAll() {
    routes.invalidateAll();
  }

  public Cache<String, Route> cache() {
    return routes;
  }
}
