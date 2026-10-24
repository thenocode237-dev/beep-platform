package io.beep.bo.adapter.in.http;

import io.beep.bo.app.auth.StaffAuthenticateUseCase;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.staff.StaffUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie de session staff (host-only, HttpOnly, SameSite=Strict, __Host- en HTTPS) et résolution du membre connecté. */
@Component
@Profile("!migrate")
public class StaffSessions {
  private final StaffAuthenticateUseCase authenticate;
  private final HttpSettings settings;

  public StaffSessions(StaffAuthenticateUseCase authenticate, HttpSettings settings) {
    this.authenticate = authenticate;
    this.settings = settings;
  }

  public StaffUser require(HttpServletRequest req) {
    return authenticate.execute(token(req)).orElseThrow(() -> new DomainException("unauthorized"));
  }

  public String token(HttpServletRequest req) {
    if (req.getCookies() == null) return null;
    for (Cookie c : req.getCookies()) if (c.getName().equals(settings.sessionCookieName())) return c.getValue();
    return null;
  }

  public ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(settings.sessionCookieName(), value).httpOnly(true).secure(settings.secureCookies()).sameSite("Strict").path("/").maxAge(maxAge).build();
  }
}
