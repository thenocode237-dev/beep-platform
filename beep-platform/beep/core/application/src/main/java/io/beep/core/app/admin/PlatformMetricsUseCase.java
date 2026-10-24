package io.beep.core.app.admin;

import io.beep.core.app.jobs.JobCatalog;
import io.beep.core.app.port.ClickSinkPort;
import io.beep.core.app.port.JobRunRepository;
import io.beep.core.app.port.PlatformMetricsRepository;
import io.beep.core.app.port.PlatformMetricsRepository.DayCount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Métriques plateforme v1 : inscriptions, activation (premier lien en 10 min), clics/jour, taille de la base, retard du worker. */
public class PlatformMetricsUseCase {
  public static final Duration ACTIVATION_WINDOW = Duration.ofMinutes(10);

  public record Metrics(
      Instant generatedAt,
      PlatformMetricsRepository.UserCounts users,
      List<DayCount> signups,
      PlatformMetricsRepository.Activation activation,
      List<DayCount> clicks,
      PlatformMetricsRepository.ResourceCounts resources,
      long databaseBytes,
      Instant lastRollupAt,
      Long lagSeconds,
      ClickSinkPort.Stats events) {
    public double activationRate() {
      return activation.signups() == 0 ? 0 : (double) activation.activated() / activation.signups();
    }
  }

  private final PlatformMetricsRepository metrics;
  private final JobRunRepository jobs;
  private final ClickSinkPort sink;
  private final Clock clock;

  public PlatformMetricsUseCase(PlatformMetricsRepository metrics, JobRunRepository jobs, ClickSinkPort sink, Clock clock) {
    this.metrics = metrics;
    this.jobs = jobs;
    this.sink = sink;
    this.clock = clock;
  }

  public Metrics execute(int days) {
    int d = Math.max(1, Math.min(days, 90));
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    LocalDate from = today.minusDays(d - 1L);
    Instant lastRollup = jobs.lastSuccess(JobCatalog.ROLLUP).orElse(null);
    return new Metrics(now, metrics.userCounts(), metrics.signupsPerDay(from, today),
        metrics.activation(from.atStartOfDay(ZoneOffset.UTC).toInstant(), ACTIVATION_WINDOW), metrics.clicksPerDay(from, today), metrics.resourceCounts(),
        metrics.databaseBytes(), lastRollup, lastRollup == null ? null : Duration.between(lastRollup, now).toSeconds(), sink.stats());
  }
}
