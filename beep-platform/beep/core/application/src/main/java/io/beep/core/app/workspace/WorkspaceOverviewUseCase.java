package io.beep.core.app.workspace;

import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.domain.plan.Entitlements;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Tableau de bord de l'espace client : plan, droits, usage face aux quotas, trafic des 30 derniers jours. */
public class WorkspaceOverviewUseCase {
  public static final int DAYS = 30;

  public record Overview(Workspace workspace, Plan plan, Entitlements entitlements, List<QuotaPolicy.Status> quotas, long clicks, List<AnalyticsRepository.Point> daily) {}

  private final Workspaces workspaces;
  private final PlanRepository plans;
  private final AnalyticsRepository analytics;
  private final Clock clock;

  public WorkspaceOverviewUseCase(Workspaces workspaces, PlanRepository plans, AnalyticsRepository analytics, Clock clock) {
    this.workspaces = workspaces;
    this.plans = plans;
    this.analytics = analytics;
    this.clock = clock;
  }

  public Overview execute(User actor) {
    Workspaces.Context ctx = workspaces.context(actor);
    Plan plan = plans.findByCode(ctx.workspace().planCode()).orElseThrow();
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    long window = ctx.entitlements().retentionDays();
    int days = window < 0 ? DAYS : (int) Math.min(DAYS, window);
    List<AnalyticsRepository.Point> daily = analytics.workspaceDaily(ctx.id(), today.minusDays(days - 1L), today);
    long clicks = daily.stream().mapToLong(AnalyticsRepository.Point::clicks).sum();
    return new Overview(ctx.workspace(), plan, ctx.entitlements(), workspaces.quotas(ctx.id(), ctx.entitlements()), clicks, daily);
  }
}
