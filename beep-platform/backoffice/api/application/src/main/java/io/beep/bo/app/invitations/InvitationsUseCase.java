package io.beep.bo.app.invitations;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;

/** Bêta privée sur invitation : liste et envoi des invitations (journalisés). */
public class InvitationsUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final Clock clock;

  public InvitationsUseCase(CoreGatewayPort core, AuditPort audit, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.clock = clock;
  }

  public CoreGatewayPort.InvitationsPage list(StaffUser actor, int limit, String cursor) {
    actor.require(Permission.INVITATIONS_WRITE);
    return core.invitations(limit, cursor, actor.id());
  }

  public CoreGatewayPort.InvitationView invite(StaffUser actor, String email, String locale, String ip) {
    actor.require(Permission.INVITATIONS_WRITE);
    Email address = new Email(email); // validation avant l'appel au core
    CoreGatewayPort.InvitationView invitation = core.invite(address.value(), "en".equals(locale) ? "en" : "fr", actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "invitations.create", "invitation:" + invitation.id(), "email=" + address.value(), ip));
    return invitation;
  }
}
