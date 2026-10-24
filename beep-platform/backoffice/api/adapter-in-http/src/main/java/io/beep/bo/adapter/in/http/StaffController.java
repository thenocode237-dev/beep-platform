package io.beep.bo.adapter.in.http;

import io.beep.bo.app.auth.StaffAuthenticateUseCase;
import io.beep.bo.app.auth.StaffLoginUseCase;
import io.beep.bo.app.auth.StaffLogoutUseCase;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.users.ListUsersUseCase;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.staff.StaffUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
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

  record MeResponse(UUID id, String email, String role) {}

  private final StaffLoginUseCase login;
  private final StaffLogoutUseCase logout;
  private final StaffAuthenticateUseCase authenticate;
  private final ListUsersUseCase listUsers;
  private final CoreGatewayPort core;
  private final HttpSettings settings;

  public StaffController(StaffLoginUseCase login, StaffLogoutUseCase logout, StaffAuthenticateUseCase authenticate, ListUsersUseCase listUsers,
      CoreGatewayPort core, HttpSettings settings) {
    this.login = login;
    this.logout = logout;
    this.authenticate = authenticate;
    this.listUsers = listUsers;
    this.core = core;
    this.settings = settings;
  }

  @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest r, HttpServletRequest req) {
    StaffLoginUseCase.Result result = login.execute(new StaffLoginUseCase.Command(r.email(), r.password(), r.totp(), req.getRemoteAddr()));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  @PostMapping(value = "/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> logout(HttpServletRequest req) {
    String token = sessionToken(req);
    logout.execute(token, authenticate.execute(token).orElse(null), req.getRemoteAddr());
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }

  @GetMapping("/auth/me")
  MeResponse me(HttpServletRequest req) {
    StaffUser u = requireStaff(req);
    return new MeResponse(u.id(), u.email().value(), u.role().db());
  }

  @GetMapping("/users")
  CoreGatewayPort.UsersPage users(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor, HttpServletRequest req) {
    return listUsers.execute(requireStaff(req), limit, cursor, req.getRemoteAddr());
  }

  /** État du core : alimente le bandeau « mode dégradé » du front. Nécessite une session. */
  @GetMapping("/status")
  Map<String, String> status(HttpServletRequest req) {
    requireStaff(req);
    return Map.of("core", core.isReachable() ? "up" : "down");
  }

  private StaffUser requireStaff(HttpServletRequest req) {
    return authenticate.execute(sessionToken(req)).orElseThrow(() -> new DomainException("unauthorized"));
  }

  private String sessionToken(HttpServletRequest req) {
    if (req.getCookies() == null) return null;
    for (Cookie c : req.getCookies()) if (c.getName().equals(settings.sessionCookieName())) return c.getValue();
    return null;
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(settings.sessionCookieName(), value).httpOnly(true).secure(settings.secureCookies()).sameSite("Strict").path("/").maxAge(maxAge).build();
  }
}
