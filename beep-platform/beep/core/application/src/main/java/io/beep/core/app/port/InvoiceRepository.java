package io.beep.core.app.port;

import io.beep.core.domain.billing.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
  /**
   * Attribue le numéro suivant de l'année (compteur verrouillé : sans trou, même en concurrence) et enregistre la facture ; le numéro du brouillon est ignoré.
   * Idempotent par paiement : si une facture existe déjà pour ce paiement, elle est retournée telle quelle.
   */
  Invoice issue(Invoice draft);

  Optional<Invoice> findByNumber(String number);

  Optional<Invoice> findByPayment(UUID paymentId);

  List<Invoice> forWorkspace(UUID workspaceId, int limit);
}
