package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.domain.link.AppEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * Catalogue d'applications. Le chemin chaud lit un instantané en mémoire (rechargé au plus toutes les 60 s, et immédiatement à chaque
 * notification `catalog:`) : aucun accès base par clic. La publication incrémente la version et conserve un instantané.
 */
public class PostgresAppCatalogAdapter implements AppCatalogPort {
  private static final long TTL_NANOS = 60_000_000_000L;
  private static final String COLUMNS = "code, name, hosts, ios_template, android_package, android_template, enabled, version";

  private final DSLContext dsl;
  private volatile List<AppEntry> snapshot = List.of();
  private volatile long loadedAt = Long.MIN_VALUE;
  private volatile boolean loaded;

  public PostgresAppCatalogAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Appelé par l'écoute des notifications : le prochain accès recharge. */
  public void invalidate() {
    loaded = false;
  }

  @Override
  public Optional<AppEntry> match(String host) {
    if (host == null || host.isBlank()) return Optional.empty();
    String h = host.toLowerCase(Locale.ROOT);
    for (AppEntry e : current()) if (e.enabled() && e.matches(h)) return Optional.of(e);
    return Optional.empty();
  }

  @Override
  public List<AppEntry> all() {
    return dsl.fetch("SELECT " + COLUMNS + " FROM app_catalog ORDER BY name").map(PostgresAppCatalogAdapter::map);
  }

  @Override
  public AppEntry publish(AppEntry e, String staffId, Instant at) {
    OffsetDateTime when = OffsetDateTime.ofInstant(at.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    int version = dsl.fetchOptional("SELECT version FROM app_catalog WHERE code = ? FOR UPDATE", e.code()).map(r -> r.get(0, Integer.class) + 1).orElse(1);
    dsl.execute("INSERT INTO app_catalog (code, name, hosts, ios_template, android_package, android_template, enabled, version, updated_by, updated_at) VALUES (?, ?, ?::text[], ?, ?, ?, ?, ?, ?, ?::timestamptz) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, hosts = EXCLUDED.hosts, ios_template = EXCLUDED.ios_template, android_package = EXCLUDED.android_package, "
            + "android_template = EXCLUDED.android_template, enabled = EXCLUDED.enabled, version = EXCLUDED.version, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at",
        e.code(), e.name(), e.hosts().toArray(new String[0]), e.iosTemplate(), e.androidPackage(), e.androidTemplate(), e.enabled(), version, staffId, when);
    String snapshotJson = Sql.json(java.util.Map.of("name", e.name(), "hosts", e.hosts(), "ios", String.valueOf(e.iosTemplate()), "package", String.valueOf(e.androidPackage()),
        "android", String.valueOf(e.androidTemplate()), "enabled", e.enabled())).data();
    dsl.execute("INSERT INTO app_catalog_versions (code, version, snapshot, published_by, published_at) VALUES (?, ?, ?::jsonb, ?, ?::timestamptz)", e.code(), version, snapshotJson, staffId, when);
    invalidate();
    return new AppEntry(e.code(), e.name(), e.hosts(), e.iosTemplate(), e.androidPackage(), e.androidTemplate(), e.enabled(), version);
  }

  @Override
  public void setEnabled(String code, boolean enabled, String staffId, Instant at) {
    dsl.execute("UPDATE app_catalog SET enabled = ?, updated_by = ?, updated_at = ?::timestamptz WHERE code = ?", enabled, staffId,
        OffsetDateTime.ofInstant(at.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC), code);
    invalidate();
  }

  private List<AppEntry> current() {
    long now = System.nanoTime();
    if (!loaded || now - loadedAt > TTL_NANOS) {
      synchronized (this) {
        if (!loaded || System.nanoTime() - loadedAt > TTL_NANOS) {
          snapshot = new ArrayList<>(dsl.fetch("SELECT " + COLUMNS + " FROM app_catalog WHERE enabled").map(PostgresAppCatalogAdapter::map));
          loadedAt = System.nanoTime();
          loaded = true;
        }
      }
    }
    return snapshot;
  }

  private static AppEntry map(Record r) {
    String[] hosts = r.get("hosts", String[].class);
    return new AppEntry(r.get("code", String.class), r.get("name", String.class), hosts == null ? List.of() : Arrays.asList(hosts), r.get("ios_template", String.class),
        r.get("android_package", String.class), r.get("android_template", String.class), r.get("enabled", Boolean.class), r.get("version", Integer.class));
  }
}
