package io.beep.bo.app.audit;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.domain.audit.AuditChain;

/** Contrôle d'intégrité du journal : retourne le numéro de la première entrée altérée, ou -1. */
public class VerifyAuditChainUseCase {
  private final AuditPort audit;

  public VerifyAuditChainUseCase(AuditPort audit) {
    this.audit = audit;
  }

  public long execute() {
    return AuditChain.verify(audit.readAll());
  }
}
