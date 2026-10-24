package io.beep.bo.app.port;

import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import java.util.List;

/** Journal d'audit en ajout seul. Aucune méthode de modification ni de suppression n'existe, volontairement. */
public interface AuditPort {
  void append(AuditEvent event);

  /** Lecture ordonnée par numéro croissant, pour la vérification d'intégrité. */
  List<AuditChain.Entry> readAll();
}
