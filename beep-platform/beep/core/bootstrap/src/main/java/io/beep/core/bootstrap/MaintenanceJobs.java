package io.beep.core.bootstrap;

import io.beep.core.app.jobs.AnalyticsMaintenanceUseCase;
import io.beep.core.app.jobs.JobCatalog;
import io.beep.core.app.jobs.JobRunner;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tâches de fond : uniquement dans le conteneur `worker` (profil `worker`). Chaque exécution est consignée (vue « Jobs »).
 * Un seul worker en phase 1 ; plusieurs workers exigeront un verrou (table de jobs SKIP LOCKED, specs §4.6).
 */
@Configuration
@Profile("worker")
@EnableScheduling
class MaintenanceJobs {
  private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

  private final SessionRepository sessions;
  private final OneTimeTokenRepository tokens;
  private final AnalyticsMaintenanceUseCase analytics;
  private final JobRunner runner;
  private final Clock clock;

  MaintenanceJobs(SessionRepository sessions, OneTimeTokenRepository tokens, AnalyticsMaintenanceUseCase analytics, JobRunner runner, Clock clock) {
    this.sessions = sessions;
    this.tokens = tokens;
    this.analytics = analytics;
    this.runner = runner;
    this.clock = clock;
  }

  /** Agrégats : la fréquence règle le délai d'affichage des statistiques (1 min ; plus court en dev pour les tests de bout en bout). */
  @Scheduled(initialDelayString = "PT10S", fixedDelayString = "${beep.jobs.rollup-interval}")
  void rollup() {
    run(JobCatalog.ROLLUP, analytics::rollup);
  }

  /** Au démarrage puis toutes les 6 h : les partitions existent toujours 2 mois à l'avance. */
  @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT6H")
  void partitions() {
    run(JobCatalog.PARTITIONS, analytics::partitions);
  }

  @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
  void retention() {
    run(JobCatalog.RETENTION, analytics::retention);
  }

  /** Purge des sessions et jetons expirés (minimisation des données). */
  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
  void purgeExpired() {
    run(JobCatalog.AUTH_PURGE, () -> "sessions=" + sessions.deleteExpired(clock.instant()) + " jetons=" + tokens.deleteExpired(clock.instant()));
  }

  private void run(String job, java.util.function.Supplier<String> work) {
    try {
      log.debug("{} : {}", job, runner.run(job, work));
    } catch (RuntimeException e) {
      log.error("tâche {} en échec", job, e); // déjà consignée dans job_runs ; la prochaine exécution réessaie
    }
  }
}
