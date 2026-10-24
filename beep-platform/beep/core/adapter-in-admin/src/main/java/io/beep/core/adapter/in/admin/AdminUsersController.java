package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.AdminUsersUseCase;
import io.beep.core.app.admin.ListUsersUseCase;
import io.beep.core.app.port.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /internal/admin/v1/users : liste (recherche), fiche, suspension/réactivation. Aucune donnée sensible exposée (ni hachage, ni secret). */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminUsersController {
  record UsersPage(List<AdminViews.UserView> items, String nextCursor) {}

  record UserDetail(AdminViews.UserView user, AdminViews.WorkspaceView workspace, List<AdminViews.UsageView> usage, Map<String, Long> entitlements,
      List<AdminViews.OverrideView> overrides) {}

  record StatusChange(String status, String reason) {}

  private final ListUsersUseCase listUsers;
  private final AdminUsersUseCase users;

  public AdminUsersController(ListUsersUseCase listUsers, AdminUsersUseCase users) {
    this.listUsers = listUsers;
    this.users = users;
  }

  @GetMapping("/users")
  UsersPage users(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor, @RequestParam(required = false) String q) {
    UserRepository.Page page = listUsers.execute(limit, cursor, q);
    return new UsersPage(page.items().stream().map(AdminViews::user).toList(), page.nextCursor());
  }

  @GetMapping("/users/{id}")
  UserDetail user(@PathVariable UUID id) {
    var d = users.detail(id);
    return new UserDetail(AdminViews.user(d.user()), AdminViews.workspace(d.workspace()), d.usage().stream().map(AdminViews::usage).toList(),
        AdminViews.sorted(d.entitlements()), d.overrides().stream().map(AdminViews::override).toList());
  }

  @PatchMapping(value = "/users/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  AdminViews.UserView status(@PathVariable UUID id, @RequestBody StatusChange r) {
    return AdminViews.user(users.setStatus(id, r.status(), r.reason()));
  }
}
