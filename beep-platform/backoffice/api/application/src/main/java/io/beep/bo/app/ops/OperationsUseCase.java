package io.beep.bo.app.ops;

import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.port.DatabaseStatsPort;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.util.List;

/** Métriques plateforme v1 (core + taille de la base du back office) et vue des jobs. Lectures agrégées, sans donnée personnelle. */
public class OperationsUseCase {
  public record Metrics(CoreGatewayPort.PlatformMetrics core, long backofficeBytes) {}

  private final CoreGatewayPort core;
  private final DatabaseStatsPort database;

  public OperationsUseCase(CoreGatewayPort core, DatabaseStatsPort database) {
    this.core = core;
    this.database = database;
  }

  public Metrics metrics(StaffUser actor, int days) {
    actor.require(Permission.METRICS_READ);
    return new Metrics(core.metrics(days, actor.id()), database.databaseBytes());
  }

  public List<CoreGatewayPort.JobView> jobs(StaffUser actor) {
    actor.require(Permission.JOBS_READ);
    return core.jobs(actor.id());
  }
}
