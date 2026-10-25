package io.beep.core.adapter.in.http;

import io.beep.core.app.auth.AuthenticateUseCase;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de session (HttpOnly, SameSite=Lax, host-only, Secure en HTTPS) et résolution de l'utilisateur connecté,
 * partagés par tous les contrôleurs de l'espace client.
 */
@Component
@Profile("!worker & !migrate")
public class SessionCookies {
  private final HttpSettings settings;
  private final AuthenticateUseCase authenticate;

  public SessionCookies(HttpSettings settings, AuthenticateUseCase authenticate) {
    this.settings = settings;
    this.authenticate = authenticate;
  }

  public String token(HttpServletRequest req) {
    return read(req, settings.sessionCookieName());
  }

  public User requireUser(HttpServletRequest req) {
    if(req.getAttribute("beep.api.user") instanceof User user)return user;
    return authenticate.execute(token(req)).orElseThrow(() -> new DomainException("unauthorized"));
  }

  public ResponseCookie session(String value, Instant expiresAt) {
    return session(value, Duration.between(Instant.now(), expiresAt));
  }

  public ResponseCookie session(String value, Duration maxAge) {
    return ResponseCookie.from(settings.sessionCookieName(), value).httpOnly(true).secure(settings.secureCookies()).sameSite("Lax").path("/").maxAge(maxAge).build();
  }

  public ResponseCookie clearSession() {
    return session("", Duration.ZERO);
  }

  /** Cookie temporaire du parcours OAuth (état + vérificateur PKCE), limité aux routes OAuth. */
  public ResponseCookie oauth(String value, Duration maxAge) {
    return ResponseCookie.from(oauthCookieName(), value).httpOnly(true).secure(settings.secureCookies()).sameSite("Lax").path("/v1/auth/oauth").maxAge(maxAge).build();
  }

  public String oauthCookie(HttpServletRequest req) {
    return read(req, oauthCookieName());
  }

  private String oauthCookieName() {
    return settings.secureCookies() ? "__Secure-beep_oauth" : "beep_oauth";
  }

  private static String read(HttpServletRequest req, String name) {
    if (req.getCookies() == null) return null;
    for (Cookie c : req.getCookies()) if (c.getName().equals(name)) return c.getValue();
    return null;
  }
}
