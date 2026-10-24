package io.beep.core.bootstrap;

import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tâches de fond : uniquement dans le conteneur `worker` (profil `worker`). */
@Configuration
@Profile("worker")
@EnableScheduling
class MaintenanceJobs {
  private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

  private final SessionRepository sessions;
  private final OneTimeTokenRepository tokens;
  private final Clock clock;

  MaintenanceJobs(SessionRepository sessions, OneTimeTokenRepository tokens, Clock clock) {
    this.sessions = sessions;
    this.tokens = tokens;
    this.clock = clock;
  }

  /** Purge des sessions et jetons expirés (minimisation des données). */
  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
  void purgeExpired() {
    int s = sessions.deleteExpired(clock.instant());
    int t = tokens.deleteExpired(clock.instant());
    log.info("purge terminée sessions={} jetons={}", s, t);
  }
}
