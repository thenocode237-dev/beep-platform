package io.beep.core.app.jobs;

import io.beep.core.app.port.JobRunRepository;
import java.time.Clock;
import java.util.function.Supplier;

/** Exécute une tâche de fond en consignant son début, sa fin, son issue et un détail lisible (vue « Jobs »). */
public class JobRunner {
  private final JobRunRepository runs;
  private final Clock clock;

  public JobRunner(JobRunRepository runs, Clock clock) {
    this.runs = runs;
    this.clock = clock;
  }

  /** Retourne le détail de l'exécution ; une erreur est consignée puis relancée (journalisée par l'ordonnanceur). */
  public String run(String job, Supplier<String> work) {
    long id = runs.start(job, clock.instant());
    try {
      String detail = work.get();
      runs.finish(id, clock.instant(), true, detail);
      return detail;
    } catch (RuntimeException e) {
      String message = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
      runs.finish(id, clock.instant(), false, message.length() > 500 ? message.substring(0, 500) : message);
      throw e;
    }
  }
}
