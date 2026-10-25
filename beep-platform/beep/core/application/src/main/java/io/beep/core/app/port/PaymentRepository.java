package io.beep.core.app.port;

import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
  /** Lève {@link DuplicateReferenceException} si la référence existe déjà chez ce fournisseur. */
  void insert(Payment payment);

  Optional<Payment> findById(UUID id);

  Optional<Payment> findByProviderRef(String provider, String providerRef);

  /** Verrouille la ligne : un seul règlement concurrent d'un même paiement (webhook, interrogation, tâche de rapprochement). */
  Optional<Payment> lockById(UUID id);

  void update(Payment payment);

  List<Payment> forWorkspace(UUID workspaceId, int limit);

  /** Paiements en attente créés avant `before` : à rapprocher auprès du fournisseur. */
  List<Payment> pendingBefore(Instant before, int limit);

  Page page(PaymentStatus status, UUID workspaceId, int limit, String cursor);

  /** Somme des paiements confirmés depuis `since` (FCFA TTC). */
  long succeededSince(Instant since);

  long countByStatus(PaymentStatus status);

  record Row(Payment payment, String workspaceName, String ownerEmail) {}

  record Page(List<Row> items, String nextCursor) {}

  class DuplicateReferenceException extends RuntimeException {
    public DuplicateReferenceException(Throwable cause) {
      super(cause);
    }
  }
}
