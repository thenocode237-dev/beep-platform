package io.beep.core.app.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Maintenance des événements (worker) : agrégats, partitions mensuelles, rétention. */
public interface RollupRepository {
  /** Recalcule entièrement les heures [from, to) depuis les événements bruts (idempotent). Retourne le nombre de lignes écrites. */
  int rollupHourly(Instant from, Instant to);

  /** Recalcule entièrement les jours [from, to] (idempotent). */
  int rollupDaily(LocalDate from, LocalDate to);

  /** Crée les partitions du mois {@code from} et des {@code monthsAhead} mois suivants (idempotent). */
  void ensurePartitions(YearMonth from, int monthsAhead);

  /** Supprime (DROP, instantané, sans bloat) les partitions entièrement antérieures à {@code keepFrom}. */
  List<String> dropPartitionsBefore(YearMonth keepFrom);

  /** Purge par lots les événements bruts hors de la fenêtre de rétention de chaque workspace (plan + override). */
  long purgeExpiredEvents(Instant now, int batchSize);

  /** Purge les agrégats hors rétention (plans à rétention limitée). */
  long purgeExpiredRollups(Instant now);
}
