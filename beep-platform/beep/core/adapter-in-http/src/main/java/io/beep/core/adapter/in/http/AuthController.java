package io.beep.core.adapter.in.http;

import io.beep.core.app.auth.AuthOutcome;
import io.beep.core.app.auth.CompleteMfaUseCase;
import io.beep.core.app.auth.EnableTotpUseCase;
import io.beep.core.app.auth.LoginUseCase;
import io.beep.core.app.auth.LogoutUseCase;
import io.beep.core.app.auth.MagicLinkUseCase;
import io.beep.core.app.auth.OAuthUseCase;
import io.beep.core.app.auth.RequestPasswordResetUseCase;
import io.beep.core.app.auth.ResetPasswordUseCase;
import io.beep.core.app.auth.SetupTotpUseCase;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.auth.VerifyEmailUseCase;
import io.beep.core.app.port.OAuthPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API d'authentification /v1/auth. Toutes les écritures exigent Content-Type: application/json
 * (requête « non simple » : un site tiers ne peut pas la forger depuis un formulaire — défense CSRF avec SameSite=Lax).
 * Exceptions : le parcours OAuth (navigation GET), protégé par l'état aléatoire lié au cookie du navigateur.
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
  record MagicRequest(String email, String locale) {}
  record MfaRequest(String token, String code) {}
  record MeResponse(UUID id, String email, boolean emailVerified, boolean totpEnabled, boolean hasPassword, String locale) {}

  private static final Duration OAUTH_TTL = Duration.ofMinutes(10);

  private final SignUpUseCase signUp;
  private final VerifyEmailUseCase verifyEmail;
  private final LoginUseCase login;
  private final LogoutUseCase logout;
  private final RequestPasswordResetUseCase requestReset;
  private final ResetPasswordUseCase reset;
  private final SetupTotpUseCase setupTotp;
  private final EnableTotpUseCase enableTotp;
  private final MagicLinkUseCase magicLink;
  private final CompleteMfaUseCase completeMfa;
  private final OAuthUseCase oauth;
  private final SessionCookies cookies;
  private final HttpSettings settings;

  public AuthController(SignUpUseCase signUp, VerifyEmailUseCase verifyEmail, LoginUseCase login, LogoutUseCase logout, RequestPasswordResetUseCase requestReset,
      ResetPasswordUseCase reset, SetupTotpUseCase setupTotp, EnableTotpUseCase enableTotp, MagicLinkUseCase magicLink, CompleteMfaUseCase completeMfa,
      OAuthUseCase oauth, SessionCookies cookies, HttpSettings settings) {
    this.signUp = signUp;
    this.verifyEmail = verifyEmail;
    this.login = login;
    this.logout = logout;
    this.requestReset = requestReset;
    this.reset = reset;
    this.setupTotp = setupTotp;
    this.enableTotp = enableTotp;
    this.magicLink = magicLink;
    this.completeMfa = completeMfa;
    this.oauth = oauth;
    this.cookies = cookies;
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
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.session(result.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> logout(HttpServletRequest req) {
    logout.execute(cookies.token(req));
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clearSession().toString()).build();
  }

  @GetMapping("/me")
  MeResponse me(HttpServletRequest req) {
    User u = cookies.requireUser(req);
    return new MeResponse(u.id(), u.email().value(), u.emailVerified(), u.totpEnabled(), u.passwordHash() != null, u.locale());
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
    return setupTotp.execute(cookies.requireUser(req));
  }

  @PostMapping(value = "/totp/enable", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> totpEnable(@RequestBody CodeRequest r, HttpServletRequest req) {
    enableTotp.execute(cookies.requireUser(req), r.code());
    return ResponseEntity.noContent().build();
  }

  // ---- Lien magique ----
  @PostMapping(value = "/magic-link", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> magicLink(@RequestBody MagicRequest r, HttpServletRequest req) {
    magicLink.request(new MagicLinkUseCase.Request(r.email(), r.locale(), req.getRemoteAddr()));
    return ResponseEntity.accepted().body(Map.of("status", "accepted"));
  }

  @PostMapping(value = "/magic-link/consume", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> consumeMagicLink(@RequestBody TokenRequest r) {
    return outcome(magicLink.consume(r.token()));
  }

  // ---- Second facteur après lien magique ou OAuth ----
  @PostMapping(value = "/mfa", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> mfa(@RequestBody MfaRequest r) {
    LoginUseCase.Result result = completeMfa.execute(r.token(), r.code());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.session(result.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  // ---- OAuth Google / GitHub ----
  @GetMapping("/providers")
  Map<String, List<String>> providers() {
    return Map.of("oauth", oauth.providers().stream().map(OAuthPort.Provider::id).toList());
  }

  @GetMapping("/oauth/{provider}/start")
  ResponseEntity<Void> oauthStart(@PathVariable String provider, @RequestParam(defaultValue = "fr") String locale) {
    OAuthUseCase.Start start = oauth.start(OAuthPort.Provider.parse(provider));
    String lang = "en".equals(locale) ? "en" : "fr";
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(start.authorizationUrl()))
        .header(HttpHeaders.SET_COOKIE, cookies.oauth(start.state() + "." + start.codeVerifier() + "." + lang, OAUTH_TTL).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  @GetMapping("/oauth/{provider}/callback")
  ResponseEntity<Void> oauthCallback(@PathVariable String provider, @RequestParam(required = false) String code, @RequestParam(required = false) String state,
      HttpServletRequest req) {
    String[] saved = String.valueOf(cookies.oauthCookie(req)).split("\\.", 3);
    String lang = saved.length == 3 ? saved[2] : "fr";
    String prefix = "en".equals(lang) ? "/en" : "";
    var clear = cookies.oauth("", Duration.ZERO).toString();
    if (saved.length != 3 || state == null || !MessageDigest.isEqual(saved[0].getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
      return redirect(prefix + "/login?error=oauth_failed", clear, null);
    }
    try {
      AuthOutcome out = oauth.complete(OAuthPort.Provider.parse(provider), code, saved[1], lang);
      if (out.mfaRequired()) return redirect(prefix + "/login/verify?token=" + URLEncoder.encode(out.mfaToken(), StandardCharsets.UTF_8), clear, null);
      return redirect(prefix + "/app", clear, cookies.session(out.sessionToken(), settings.sessionTtl()).toString());
    } catch (DomainException e) {
      return redirect(prefix + "/login?error=" + e.code(), clear, null);
    }
  }

  private ResponseEntity<Map<String, Object>> outcome(AuthOutcome out) {
    if (out.mfaRequired()) return ResponseEntity.ok(Map.of("status", "mfa_required", "token", out.mfaToken()));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.session(out.sessionToken(), settings.sessionTtl()).toString()).body(Map.of("status", "ok"));
  }

  private static ResponseEntity<Void> redirect(String path, String clearCookie, String sessionCookie) {
    var b = ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).header(HttpHeaders.SET_COOKIE, clearCookie).header(HttpHeaders.CACHE_CONTROL, "no-store");
    if (sessionCookie != null) b = b.header(HttpHeaders.SET_COOKIE, sessionCookie);
    return b.build();
  }
}
