package io.beep.core.app.jobs;

import io.beep.core.app.port.JobRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tâches de fond du worker et lecture de leur état (vue « Jobs » du back office). */
public class JobCatalog {
  public static final String ROLLUP = "analytics.rollup";
  public static final String PARTITIONS = "analytics.partitions";
  public static final String RETENTION = "analytics.retention";
  public static final String AUTH_PURGE = "auth.purge";

  public record Job(String name, String description, String schedule) {}

  public record JobStatus(Job job, JobRunRepository.JobSummary summary) {}

  public static final List<Job> JOBS = List.of(
      new Job(ROLLUP, "Agrégats horaires et journaliers des clics, scans et vues", "PT1M"),
      new Job(PARTITIONS, "Partitions mensuelles des événements (création à 2 mois, suppression après 13 mois)", "PT6H"),
      new Job(RETENTION, "Purge des événements et agrégats hors rétention du plan, sels quotidiens, historique des jobs", "PT1H"),
      new Job(AUTH_PURGE, "Purge des sessions et jetons expirés", "PT15M"));

  private final JobRunRepository runs;
  private final Clock clock;

  public JobCatalog(JobRunRepository runs, Clock clock) {
    this.runs = runs;
    this.clock = clock;
  }

  /** État des 24 dernières heures de chaque tâche connue (même si elle n'a jamais tourné). */
  public List<JobStatus> statuses() {
    Map<String, JobRunRepository.JobSummary> byName = runs.summaries(clock.instant().minus(Duration.ofHours(24))).stream()
        .collect(Collectors.toMap(JobRunRepository.JobSummary::job, Function.identity()));
    return JOBS.stream().map(j -> new JobStatus(j, byName.get(j.name()))).toList();
  }
}
