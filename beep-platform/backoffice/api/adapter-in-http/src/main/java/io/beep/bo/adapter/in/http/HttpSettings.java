package io.beep.bo.adapter.in.http;

import java.time.Duration;

/** Cookie de session staff : host-only, HttpOnly, SameSite=Strict ; préfixe __Host- en HTTPS. */
public record HttpSettings(boolean secureCookies, Duration sessionTtl) {
  public String sessionCookieName() {
    return secureCookies ? "__Host-bo_session" : "bo_session";
  }
}
