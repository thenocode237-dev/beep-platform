package io.beep.core.app.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Historique d'exécution des tâches de fond (vue « Jobs » du back office, retard du worker). */
public interface JobRunRepository {
  long start(String job, Instant at);

  void finish(long runId, Instant at, boolean ok, String detail);

  List<JobSummary> summaries(Instant since);

  Optional<Instant> lastSuccess(String job);

  int deleteBefore(Instant cutoff);

  record JobSummary(String job, Instant lastStartedAt, Instant lastFinishedAt, Boolean lastOk, String lastDetail, Instant lastSuccessAt, long runs, long failures) {}
}
