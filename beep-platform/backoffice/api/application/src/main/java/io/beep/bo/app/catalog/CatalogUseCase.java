package io.beep.bo.app.catalog;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.List;

/** Catalogue d'applications : lecture et test en direct pour tous ceux qui ont le droit de lecture ; publication versionnée avec TOTP, journalisée. */
public class CatalogUseCase {
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public CatalogUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public List<CoreGatewayPort.AppView> list(StaffUser actor) {
    actor.require(Permission.CATALOG_READ);
    return core.catalog(actor.id());
  }

  public CoreGatewayPort.AppTestResult test(StaffUser actor, String url, String profile, CoreGatewayPort.AppView candidate) {
    actor.require(Permission.CATALOG_READ);
    return core.testApp(url, profile, candidate, actor.id());
  }

  public CoreGatewayPort.AppView publish(StaffUser actor, String code, String name, List<String> hosts, String ios, String pkg, String android, boolean enabled, String totp,
      String ip) {
    actor.require(Permission.CATALOG_WRITE);
    stepUp.require(actor, totp);
    CoreGatewayPort.AppView app = core.publishApp(code, name, hosts, ios, pkg, android, enabled, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "catalog.publish", "app:" + code, "version=" + app.version() + " hôtes=" + String.join(",", hosts), ip));
    return app;
  }

  public List<CoreGatewayPort.AppView> setEnabled(StaffUser actor, String code, boolean enabled, String totp, String ip) {
    actor.require(Permission.CATALOG_WRITE);
    stepUp.require(actor, totp);
    var out = core.setAppEnabled(code, enabled, actor.id());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), enabled ? "catalog.enable" : "catalog.disable", "app:" + code, null, ip));
    return out;
  }
}
