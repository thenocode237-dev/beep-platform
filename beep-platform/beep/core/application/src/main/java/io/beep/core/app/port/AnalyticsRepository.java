package io.beep.core.app.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lecture des agrégats (rollups) : les tableaux de bord ne lisent jamais les événements bruts. Jours en UTC. */
public interface AnalyticsRepository {
  /** Type de ressource agrégée : liens (clics + scans), QR dynamiques (scans), vues et clics de blocs des pages bio. */
  enum Resource {
    LINK("link"),
    QR("qr"),
    BIO("bio"),
    BIO_CLICK("bio_click");

    private final String db;

    Resource(String db) {
      this.db = db;
    }

    public String db() {
      return db;
    }
  }

  Totals totals(Resource resource, UUID id, LocalDate from, LocalDate to);

  /** Série journalière complète (jours sans trafic inclus, à zéro). */
  List<Point> daily(Resource resource, UUID id, LocalDate from, LocalDate to);

  /** Série horaire [from, to) complète. */
  List<Point> hourly(Resource resource, UUID id, Instant from, Instant to);

  List<Breakdown> breakdown(Resource resource, UUID id, String dimension, LocalDate from, LocalDate to, int top);

  Map<UUID, Long> totalsByResource(Resource resource, Collection<UUID> ids, LocalDate from, LocalDate to);

  /** Clics et scans de tous les liens d'un workspace, par jour. */
  List<Point> workspaceDaily(UUID workspaceId, LocalDate from, LocalDate to);

  record Totals(long clicks, long uniques) {}

  /** `bucket` : date ISO (série journalière) ou instant ISO de début d'heure (série horaire). */
  record Point(String bucket, long clicks, long uniques) {}

  record Breakdown(String key, long clicks) {}
}
