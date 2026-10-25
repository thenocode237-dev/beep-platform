package io.beep.bo.app.domains;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.UUID;

/** Vue « Domaines » : liste globale, état DNS/TLS, relance de la vérification, blocage (motif + TOTP) et déblocage ; tout est journalisé. */
public class DomainsUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public DomainsUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public CoreGatewayPort.DomainsPage list(StaffUser actor, String query, String status, int limit, String cursor) {
    actor.require(Permission.DOMAINS_READ);
    return core.domains(query, status, limit, cursor, actor.id());
  }

  public CoreGatewayPort.DomainView get(StaffUser actor, UUID id) {
    actor.require(Permission.DOMAINS_READ);
    return core.domain(id, actor.id());
  }

  public CoreGatewayPort.DomainView recheck(StaffUser actor, UUID id, String ip) {
    actor.require(Permission.DOMAINS_MODERATE);
    CoreGatewayPort.DomainView d = core.recheckDomain(id, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "domains.recheck", "domain:" + d.hostname(), "dns=" + d.dnsStatus() + " erreur=" + d.lastError(), ip));
    return d;
  }

  public CoreGatewayPort.DomainView block(StaffUser actor, UUID id, String reason, String totp, String ip) {
    actor.require(Permission.DOMAINS_MODERATE);
    if (reason == null || reason.strip().length() < 3) throw new DomainException("invalid_reason");
    stepUp.require(actor, totp);
    CoreGatewayPort.DomainView d = core.blockDomain(id, reason.strip(), actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "domains.block", "domain:" + d.hostname(), "liens=" + d.links() + " motif=" + reason.strip(), ip));
    return d;
  }

  public CoreGatewayPort.DomainView unblock(StaffUser actor, UUID id, String totp, String ip) {
    actor.require(Permission.DOMAINS_MODERATE);
    stepUp.require(actor, totp);
    CoreGatewayPort.DomainView d = core.unblockDomain(id, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "domains.unblock", "domain:" + d.hostname(), null, ip));
    return d;
  }
}
