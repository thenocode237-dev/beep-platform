package io.beep.core.adapter.out.postgres;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.beep.core.app.port.SlugResolverPort;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache mémoire du chemin chaud (specs §4.5) : un succès de cache ne touche pas la base.
 * Invalidation précise par LISTEN/NOTIFY ({@link CacheInvalidationListener}) ; la durée de vie n'est qu'un filet de sécurité.
 * Les slugs inconnus sont aussi mis en cache (30 s) pour qu'un balayage de chemins inexistants n'atteigne pas la base.
 */
public class CachingSlugResolverAdapter implements SlugResolverPort {
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);

  private final SlugResolverPort delegate;
  private final Cache<String, Resolution> slugs;
  private final Cache<String, Optional<QrTarget>> qrCodes;
  private final Cache<UUID, Optional<PublishedBio>> bioPages;

  public CachingSlugResolverAdapter(SlugResolverPort delegate, long maxEntries) {
    this.delegate = delegate;
    this.slugs = Caffeine.newBuilder().maximumSize(maxEntries).recordStats().expireAfter(new Expiry<String, Resolution>() {
      public long expireAfterCreate(String k, Resolution v, long now) {
        return (v.kind() == Resolution.Kind.NONE ? NEGATIVE_TTL : TTL).toNanos();
      }

      public long expireAfterUpdate(String k, Resolution v, long now, long current) {
        return expireAfterCreate(k, v, now);
      }

      public long expireAfterRead(String k, Resolution v, long now, long current) {
        return current;
      }
    }).build();
    this.qrCodes = Caffeine.newBuilder().maximumSize(maxEntries).expireAfterWrite(TTL).recordStats().build();
    this.bioPages = Caffeine.newBuilder().maximumSize(Math.max(1000, maxEntries / 10)).expireAfterWrite(TTL).recordStats().build();
  }

  @Override
  public Resolution resolve(String slug) {
    return slugs.get(slug, delegate::resolve);
  }

  @Override
  public Optional<QrTarget> resolveQr(String code) {
    return qrCodes.get(code, delegate::resolveQr);
  }

  @Override
  public Optional<PublishedBio> bioPage(UUID pageId) {
    return bioPages.get(pageId, delegate::bioPage);
  }

  /** Charge utile des notifications `beep_cache` : slug:…, link:…, qr:…, bio:…, ws:… */
  public void invalidate(String payload) {
    int sep = payload.indexOf(':');
    if (sep < 0) {
      invalidateAll();
      return;
    }
    String kind = payload.substring(0, sep);
    String value = payload.substring(sep + 1);
    switch (kind) {
      case "slug" -> slugs.invalidate(value);
      case "qr" -> qrCodes.invalidate(value);
      case "link" -> qrCodes.asMap().entrySet().removeIf(e -> e.getValue().map(t -> t.linkId().toString().equals(value)).orElse(false));
      case "bio" -> {
        try {
          bioPages.invalidate(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
          bioPages.invalidateAll();
        }
      }
      default -> invalidateAll(); // ws:… (suspension) ou inconnu : rare, on repart de zéro
    }
  }

  public void invalidateAll() {
    slugs.invalidateAll();
    qrCodes.invalidateAll();
    bioPages.invalidateAll();
  }

  /** Pour la supervision (taux de succès exposés par Micrometer). */
  public Map<String, Cache<?, ?>> caches() {
    return Map.of("redirect.slugs", slugs, "redirect.qr", qrCodes, "redirect.bio", bioPages);
  }
}
