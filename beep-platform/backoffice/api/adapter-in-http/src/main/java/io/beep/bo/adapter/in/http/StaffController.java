package io.beep.bo.adapter.in.http;

import io.beep.bo.app.auth.StaffLoginUseCase;
import io.beep.bo.app.auth.StaffLogoutUseCase;
import io.beep.bo.app.auth.StaffAuthenticateUseCase;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.users.ListUsersUseCase;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API du back office : /api/* (routée par Caddy vers ce service). Cookie SameSite=Strict + JSON obligatoire en écriture. */
@RestController
@RequestMapping("/api")
@Profile("!migrate") // le profil migrate n'exécute que Flyway
public class StaffController {
  record LoginRequest(String email, String password, String totp) {}

  record MeResponse(UUID id, String email, String role, List<String> permissions) {}

  private final StaffLoginUseCase login;
  private final StaffLogoutUseCase logout;
  private final StaffAuthenticateUseCase authenticate;
  private final ListUsersUseCase listUsers;
  private final CoreGatewayPort core;
  private final StaffSessions sessions;
  private final HttpSettings settings;

  public StaffController(StaffLoginUseCase login, StaffLogoutUseCase logout, StaffAuthenticateUseCase authenticate, ListUsersUseCase listUsers,
      CoreGatewayPort core, StaffSessions sessions, HttpSettings settings) {
    this.login = login;
    this.logout = logout;
    this.authenticate = authenticate;
    this.listUsers = listUsers;
    this.core = core;
    this.sessions = sessions;
    this.settings = settings;
  }

  @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest r, HttpServletRequest req) {
    StaffLoginUseCase.Result result = login.execute(new StaffLoginUseCase.Command(r.email(), r.password(), r.totp(), req.getRemoteAddr()));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, sessions.cookie(result.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  @PostMapping(value = "/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> logout(HttpServletRequest req) {
    String token = sessions.token(req);
    logout.execute(token, authenticate.execute(token).orElse(null), req.getRemoteAddr());
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.cookie("", Duration.ZERO).toString()).build();
  }

  /** Le front n'affiche que les modules permis ; le contrôle réel reste côté cas d'usage. */
  @GetMapping("/auth/me")
  MeResponse me(HttpServletRequest req) {
    StaffUser u = sessions.require(req);
    return new MeResponse(u.id(), u.email().value(), u.role().db(), u.role().permissions().stream().map(Permission::name).sorted().toList());
  }

  @GetMapping("/users")
  CoreGatewayPort.UsersPage users(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String q, HttpServletRequest req) {
    return listUsers.execute(sessions.require(req), limit, cursor, q, req.getRemoteAddr());
  }

  /** État du core : alimente le bandeau « mode dégradé » du front. Nécessite une session. */
  @GetMapping("/status")
  Map<String, String> status(HttpServletRequest req) {
    sessions.require(req);
    return Map.of("core", core.isReachable() ? "up" : "down");
  }
}
