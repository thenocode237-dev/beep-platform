package io.beep.core.app.port;

import io.beep.core.domain.billing.Invoice;

/** Rendu PDF d'une facture. */
public interface InvoicePdfPort {
  byte[] render(Invoice invoice);
}
