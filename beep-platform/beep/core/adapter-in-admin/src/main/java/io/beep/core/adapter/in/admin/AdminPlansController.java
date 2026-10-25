package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.AdminEntitlementsUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module « Plans & droits » : plans publiés, overrides par workspace. */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminPlansController {
  record PlanView(String code, String name, int version, long priceXaf, long priceQuarterlyXaf, long priceAnnualXaf, @com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic,
      Map<String, Long> entitlements) {}

  record PlansList(List<PlanView> items) {}

  record OverridesChange(Map<String, Long> overrides, String reason) {}

  record WorkspaceEntitlements(Map<String, Long> entitlements, List<AdminViews.OverrideView> overrides) {}

  private final AdminEntitlementsUseCase entitlements;

  public AdminPlansController(AdminEntitlementsUseCase entitlements) {
    this.entitlements = entitlements;
  }

  @GetMapping("/plans")
  PlansList plans() {
    return new PlansList(entitlements.plans().stream()
        .map(p -> new PlanView(p.code(), p.name(), p.version(), p.priceXaf(), p.priceQuarterlyXaf(), p.priceAnnualXaf(), p.publicPlan(), AdminViews.sorted(p.entitlements()))).toList());
  }

  @PutMapping(value = "/workspaces/{id}/entitlements", consumes = MediaType.APPLICATION_JSON_VALUE)
  WorkspaceEntitlements set(@PathVariable UUID id, @RequestBody OverridesChange r, @RequestHeader(value = "X-Staff-Id", required = false) String staff) {
    var result = entitlements.setOverrides(id, r.overrides(), r.reason(), AdminViews.staff(staff));
    return new WorkspaceEntitlements(AdminViews.sorted(result.entitlements()), result.overrides().stream().map(AdminViews::override).toList());
  }
}
