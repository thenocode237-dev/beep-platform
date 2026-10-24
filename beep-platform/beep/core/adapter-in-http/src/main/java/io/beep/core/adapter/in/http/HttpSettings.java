package io.beep.core.adapter.in.http;

import java.time.Duration;

/** Réglages de la couche HTTP. Le préfixe __Host- n'est utilisable qu'en HTTPS (Secure, Path=/, sans Domain). */
public record HttpSettings(boolean secureCookies, Duration sessionTtl) {
  public String sessionCookieName() {
    return secureCookies ? "__Host-beep_session" : "beep_session";
  }
}
