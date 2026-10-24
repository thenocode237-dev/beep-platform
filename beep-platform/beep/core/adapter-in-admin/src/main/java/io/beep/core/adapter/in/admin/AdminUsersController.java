package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.ListUsersUseCase;
import io.beep.core.app.port.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /internal/admin/v1/users : implémentation du contrat contracts/beep-admin.openapi.yaml. Aucune donnée sensible exposée. */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminUsersController {
  record UserView(UUID id, String email, String status, boolean emailVerified, boolean totpEnabled, Instant createdAt) {}

  record UsersPage(List<UserView> items, String nextCursor) {}

  private final ListUsersUseCase listUsers;

  public AdminUsersController(ListUsersUseCase listUsers) {
    this.listUsers = listUsers;
  }

  @GetMapping("/users")
  UsersPage users(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
    UserRepository.Page page = listUsers.execute(limit, cursor);
    return new UsersPage(
        page.items().stream().map(u -> new UserView(u.id(), u.email().value(), u.status().db(), u.emailVerified(), u.totpEnabled(), u.createdAt())).toList(),
        page.nextCursor());
  }
}
