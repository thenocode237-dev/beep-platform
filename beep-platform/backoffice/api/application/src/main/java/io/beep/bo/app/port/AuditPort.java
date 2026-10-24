package io.beep.bo.app.port;

import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import java.util.List;

/** Journal d'audit en ajout seul. Aucune méthode de modification ni de suppression n'existe, volontairement. */
public interface AuditPort {
  void append(AuditEvent event);

  /** Lecture ordonnée par numéro croissant, pour la vérification d'intégrité. */
  List<AuditChain.Entry> readAll();

  /** Consultation paginée, plus récentes d'abord ; filtres facultatifs (préfixe d'action, e-mail de l'auteur). */
  Page page(int limit, Long beforeSeq, String actionPrefix, String actorEmail);

  record Page(List<AuditChain.Entry> items, Long nextBeforeSeq) {}
}
