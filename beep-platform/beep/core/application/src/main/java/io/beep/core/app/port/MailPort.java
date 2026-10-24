package io.beep.core.app.port;

import io.beep.core.domain.user.Email;

public interface MailPort {
  void sendVerification(Email to, String locale, String token);

  void sendPasswordReset(Email to, String locale, String token);

  /** Envoyé à la place d'une confirmation quand l'adresse a déjà un compte (anti-énumération côté API). */
  void sendAlreadyRegistered(Email to, String locale);
}
