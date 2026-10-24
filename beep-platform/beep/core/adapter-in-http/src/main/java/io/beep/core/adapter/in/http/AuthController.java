package io.beep.core.adapter.in.http;

import io.beep.core.app.auth.AuthenticateUseCase;
import io.beep.core.app.auth.EnableTotpUseCase;
import io.beep.core.app.auth.LoginUseCase;
import io.beep.core.app.auth.LogoutUseCase;
import io.beep.core.app.auth.RequestPasswordResetUseCase;
import io.beep.core.app.auth.ResetPasswordUseCase;
import io.beep.core.app.auth.SetupTotpUseCase;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.auth.VerifyEmailUseCase;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.User;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * API d'authentification /v1/auth. Toutes les écritures exigent Content-Type: application/json
 * (requête « non simple » : un site tiers ne peut pas la forger depuis un formulaire — défense CSRF avec SameSite=Lax).
 */
@RestController
@RequestMapping("/v1/auth")
@Profile("!worker & !migrate")
public class AuthController {
  record SignupRequest(String email, String password, String locale) {}
  record LoginRequest(String email, String password, String totp) {}
  record TokenRequest(String token) {}
  record ForgotRequest(String email) {}
  record ResetRequest(String token, String password) {}
  record CodeRequest(String code) {}
  record MeResponse(UUID id, String email, boolean emailVerified, boolean totpEnabled) {}

  private final SignUpUseCase signUp;
  private final VerifyEmailUseCase verifyEmail;
  private final LoginUseCase login;
  private final LogoutUseCase logout;
  private final AuthenticateUseCase authenticate;
  private final RequestPasswordResetUseCase requestReset;
  private final ResetPasswordUseCase reset;
  private final SetupTotpUseCase setupTotp;
  private final EnableTotpUseCase enableTotp;
  private final HttpSettings settings;

  public AuthController(SignUpUseCase signUp, VerifyEmailUseCase verifyEmail, LoginUseCase login, LogoutUseCase logout, AuthenticateUseCase authenticate,
      RequestPasswordResetUseCase requestReset, ResetPasswordUseCase reset, SetupTotpUseCase setupTotp, EnableTotpUseCase enableTotp, HttpSettings settings) {
    this.signUp = signUp;
    this.verifyEmail = verifyEmail;
    this.login = login;
    this.logout = logout;
    this.authenticate = authenticate;
    this.requestReset = requestReset;
    this.reset = reset;
    this.setupTotp = setupTotp;
    this.enableTotp = enableTotp;
    this.settings = settings;
  }

  @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest r, HttpServletRequest req) {
    signUp.execute(new SignUpUseCase.Command(r.email(), r.password(), r.locale(), req.getRemoteAddr()));
    return ResponseEntity.accepted().body(Map.of("status", "accepted")); // identique que l'adresse existe ou non
  }

  @PostMapping(value = "/verify-email", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> verify(@RequestBody TokenRequest r) {
    verifyEmail.execute(r.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest r, HttpServletRequest req) {
    LoginUseCase.Result result = login.execute(new LoginUseCase.Command(r.email(), r.password(), r.totp(), req.getRemoteAddr()));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> logout(HttpServletRequest req) {
    logout.execute(sessionToken(req));
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }

  @GetMapping("/me")
  MeResponse me(HttpServletRequest req) {
    User u = requireUser(req);
    return new MeResponse(u.id(), u.email().value(), u.emailVerified(), u.totpEnabled());
  }

  @PostMapping(value = "/password/forgot", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> forgot(@RequestBody ForgotRequest r, HttpServletRequest req) {
    requestReset.execute(new RequestPasswordResetUseCase.Command(r.email(), req.getRemoteAddr()));
    return ResponseEntity.accepted().body(Map.of("status", "accepted"));
  }

  @PostMapping(value = "/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> reset(@RequestBody ResetRequest r) {
    reset.execute(r.token(), r.password());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/totp/setup", consumes = MediaType.APPLICATION_JSON_VALUE)
  SetupTotpUseCase.Setup totpSetup(HttpServletRequest req) {
    return setupTotp.execute(requireUser(req));
  }

  @PostMapping(value = "/totp/enable", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> totpEnable(@RequestBody CodeRequest r, HttpServletRequest req) {
    enableTotp.execute(requireUser(req), r.code());
    return ResponseEntity.noContent().build();
  }

  private User requireUser(HttpServletRequest req) {
    return authenticate.execute(sessionToken(req)).orElseThrow(() -> new DomainException("unauthorized"));
  }

  private String sessionToken(HttpServletRequest req) {
    if (req.getCookies() == null) return null;
    for (Cookie c : req.getCookies()) if (c.getName().equals(settings.sessionCookieName())) return c.getValue();
    return null;
  }

  /** Cookie de session : HttpOnly, SameSite=Lax, host-only (aucun attribut Domain) et Secure en HTTPS. */
  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(settings.sessionCookieName(), value).httpOnly(true).secure(settings.secureCookies()).sameSite("Lax").path("/").maxAge(maxAge).build();
  }
}
