package io.beep.core.app.jobs;

import io.beep.core.app.port.JobRunRepository;
import io.beep.core.app.port.RollupRepository;
import io.beep.core.app.port.VisitorSaltRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tâches du worker sur les événements :
 *  - agrégats : recalcul complet des 3 dernières heures et des 2 derniers jours (rattrape les écritures tardives, idempotent) ;
 *  - partitions : mois courant + 2 mois d'avance ; suppression des mois au-delà de 13 mois (événements bruts) ;
 *  - rétention : purge selon le plan (Starter : 30 jours), sels de plus de 2 jours, historique des jobs de plus de 7 jours.
 */
public class AnalyticsMaintenanceUseCase {
  public static final int RAW_RETENTION_MONTHS = 13;
  public static final int PARTITIONS_AHEAD = 2;
  static final int PURGE_BATCH = 5_000;

  private final RollupRepository rollups;
  private final VisitorSaltRepository salts;
  private final JobRunRepository jobRuns;
  private final Clock clock;

  public AnalyticsMaintenanceUseCase(RollupRepository rollups, VisitorSaltRepository salts, JobRunRepository jobRuns, Clock clock) {
    this.rollups = rollups;
    this.salts = salts;
    this.jobRuns = jobRuns;
    this.clock = clock;
  }

  public String rollup() {
    Instant hour = clock.instant().truncatedTo(ChronoUnit.HOURS);
    LocalDate today = LocalDate.ofInstant(hour, ZoneOffset.UTC);
    int hourly = rollups.rollupHourly(hour.minus(Duration.ofHours(2)), hour.plus(Duration.ofHours(1)));
    int daily = rollups.rollupDaily(today.minusDays(1), today);
    return "horaires=" + hourly + " journaliers=" + daily;
  }

  public String partitions() {
    rollups.ensurePartitions(YearMonth.now(clock.withZone(ZoneOffset.UTC)), PARTITIONS_AHEAD);
    List<String> dropped = rollups.dropPartitionsBefore(YearMonth.now(clock.withZone(ZoneOffset.UTC)).minusMonths(RAW_RETENTION_MONTHS));
    return "supprimées=" + (dropped.isEmpty() ? "aucune" : String.join(",", dropped));
  }

  public String retention() {
    Instant now = clock.instant();
    long events = rollups.purgeExpiredEvents(now, PURGE_BATCH);
    long aggregates = rollups.purgeExpiredRollups(now);
    int saltsDeleted = salts.deleteBefore(LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(1));
    int runs = jobRuns.deleteBefore(now.minus(Duration.ofDays(7)));
    return "événements=" + events + " agrégats=" + aggregates + " sels=" + saltsDeleted + " historique=" + runs;
  }
}
