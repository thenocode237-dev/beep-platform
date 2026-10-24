package io.beep.core.app.auth;

import java.time.Instant;

/**
 * Issue d'un premier facteur sans mot de passe (lien magique, OAuth) : soit une session, soit un jeton de second facteur
 * quand le compte a activé le TOTP (le code reste exigé, quel que soit le moyen de connexion).
 */
public record AuthOutcome(String sessionToken, Instant expiresAt, String mfaToken) {
  public boolean mfaRequired() {
    return mfaToken != null;
  }
}
