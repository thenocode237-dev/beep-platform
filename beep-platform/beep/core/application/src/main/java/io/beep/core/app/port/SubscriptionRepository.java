package io.beep.core.app.port;

import io.beep.core.domain.billing.Subscription;
import io.beep.core.domain.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
  Optional<Subscription> findByWorkspace(UUID workspaceId);

  /** Verrouille la ligne du workspace jusqu'à la fin de la transaction (paiements concurrents, worker). */
  Optional<Subscription> lockByWorkspace(UUID workspaceId);

  /** Crée ou remplace l'abonnement du workspace. */
  void save(Subscription subscription);

  /** Abonnements dont l'état peut devoir changer ou qui appellent un rappel : non expirés dont l'échéance est avant `horizon`, et expirés à mettre en pause. */
  List<Subscription> candidates(Instant horizon, int limit);

  List<Row> page(SubscriptionStatus status, int limit, int offset);

  Summary summary();

  /** Échéances à venir : périodes payées ou d'essai qui se terminent avant `until`. */
  List<Row> upcoming(Instant now, Instant until, int limit);

  record Row(Subscription subscription, String workspaceName, String ownerEmail) {}

  record Summary(long trialing, long active, long grace, long expired, long mrrXaf) {}
}
