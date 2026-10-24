package io.beep.bo.app.users;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;

/** Liste des utilisateurs de la plateforme (lecture seule, via l'API admin du core), avec recherche. La consultation est journalisée. */
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
    return execute(actor, limit, cursor, null, ip);
  }

  public CoreGatewayPort.UsersPage execute(StaffUser actor, int limit, String cursor, String query, String ip) {
    actor.require(Permission.USERS_READ);
    CoreGatewayPort.UsersPage page = core.listUsers(limit, cursor, query, actor.id());
    String detail = "count=" + page.items().size() + (query == null || query.isBlank() ? "" : " q=" + query.strip());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "users.list", "users", detail, ip));
    return page;
  }
}
