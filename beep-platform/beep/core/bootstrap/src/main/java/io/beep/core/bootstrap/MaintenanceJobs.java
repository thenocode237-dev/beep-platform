package io.beep.core.bootstrap;

import io.beep.core.app.billing.BillingLifecycleUseCase;
import io.beep.core.app.billing.SettlePaymentUseCase;
import io.beep.core.app.jobs.AnalyticsMaintenanceUseCase;
import io.beep.core.app.jobs.JobCatalog;
import io.beep.core.app.jobs.JobRunner;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.domain.billing.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
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
  private final BillingLifecycleUseCase billing;
  private final SettlePaymentUseCase settlement;
  private final JobRunner runner;
  private final Clock clock;
  private final io.beep.core.app.trust.TrustUseCase trust;
  private final io.beep.core.app.port.UrlSafetyPort safety;
  private final io.beep.core.app.billing.QuotaLifecycleUseCase quotas;

  MaintenanceJobs(SessionRepository sessions, OneTimeTokenRepository tokens, AnalyticsMaintenanceUseCase analytics, BillingLifecycleUseCase billing,
      SettlePaymentUseCase settlement, JobRunner runner, Clock clock, io.beep.core.app.trust.TrustUseCase trust, io.beep.core.app.port.UrlSafetyPort safety, io.beep.core.app.billing.QuotaLifecycleUseCase quotas) {
    this.trust=trust; this.safety=safety; this.quotas=quotas;
    this.sessions = sessions;
    this.tokens = tokens;
    this.analytics = analytics;
    this.billing = billing;
    this.settlement = settlement;
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

  /** Échéances : rappels J−7/J−3/J−1, période de grâce, retour au plan Starter (specs §6.4). */
  @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT15M")
  void billingLifecycle() {
    run(JobCatalog.BILLING_LIFECYCLE, () -> billing.run().detail());
  }

  /** Rapproche les paiements restés en attente (notification perdue, client parti avant de confirmer). */
  @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${beep.billing.reconcile-interval}")
  void billingReconcile() {
    run(JobCatalog.BILLING_RECONCILE, () -> {
      var ids = billing.pendingToReconcile(Duration.ofMinutes(1), 50);
      int settled = 0;
      for (var id : ids) if (settlement.settle(id).status() != PaymentStatus.PENDING) settled++;
      return "en attente=" + ids.size() + " réglés=" + settled;
    });
  }

  @Scheduled(initialDelayString="PT1M",fixedDelayString="PT5M")
  void safety() {run("trust.scan",() -> trust.scan(safety));}
  @Scheduled(initialDelayString="PT2M",fixedDelayString="PT1H")
  void quotas() {run("billing.quotas",quotas::run);}

  private void run(String job, java.util.function.Supplier<String> work) {
    try {
      log.debug("{} : {}", job, runner.run(job, work));
    } catch (RuntimeException e) {
      log.error("tâche {} en échec", job, e); // déjà consignée dans job_runs ; la prochaine exécution réessaie
    }
  }
}
