package io.beep.core.adapter.in.http;

import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.workspace.WorkspaceOverviewUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /v1/workspace : plan, droits, usage face aux quotas (avertissement 80 %, blocage 100 %), trafic des 30 derniers jours. */
@RestController
@RequestMapping("/v1/workspace")
@Profile("!worker & !migrate")
public class WorkspaceController {
  record PlanView(String code, String name, long priceXaf) {}

  record WorkspaceView(UUID id, String name, String status, PlanView plan, Map<String, Long> entitlements, List<Views.QuotaView> quotas, long clicks30d,
      List<AnalyticsRepository.Point> daily, Instant createdAt) {}

  private final WorkspaceOverviewUseCase overview;
  private final SessionCookies cookies;

  public WorkspaceController(WorkspaceOverviewUseCase overview, SessionCookies cookies) {
    this.overview = overview;
    this.cookies = cookies;
  }

  @GetMapping
  WorkspaceView get(HttpServletRequest req) {
    var o = overview.execute(cookies.requireUser(req));
    return new WorkspaceView(o.workspace().id(), o.workspace().name(), o.workspace().status().db(), new PlanView(o.plan().code(), o.plan().name(), o.plan().priceXaf()),
        Views.entitlements(o.entitlements().values()), o.quotas().stream().map(Views::quota).toList(), o.clicks(), o.daily(), o.workspace().createdAt());
  }
}
