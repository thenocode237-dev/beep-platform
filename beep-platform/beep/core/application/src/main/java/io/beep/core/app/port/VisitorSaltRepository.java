package io.beep.core.app.port;

import java.time.LocalDate;

/**
 * Sel quotidien aléatoire des empreintes de visiteurs, partagé par toutes les instances et détruit après deux jours :
 * une fois le sel effacé, une empreinte ne peut plus être rapprochée d'une IP.
 */
public interface VisitorSaltRepository {
  byte[] saltFor(LocalDate day);

  int deleteBefore(LocalDate day);
}
