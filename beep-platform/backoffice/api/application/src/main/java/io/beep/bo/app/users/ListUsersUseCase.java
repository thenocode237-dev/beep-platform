package io.beep.bo.app.users;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;

/** Liste des utilisateurs de la plateforme (lecture seule, via l'API admin du core). La consultation est journalisée. */
public class ListUsersUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final Clock clock;

  public ListUsersUseCase(CoreGatewayPort core, AuditPort audit, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.clock = clock;
  }

  public CoreGatewayPort.UsersPage execute(StaffUser actor, int limit, String cursor, String ip) {
    actor.require(Permission.USERS_READ);
    CoreGatewayPort.UsersPage page = core.listUsers(limit, cursor, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "users.list", "users", "count=" + page.items().size(), ip));
    return page;
  }
}
