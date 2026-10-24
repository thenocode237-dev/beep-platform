package io.beep.core.app.port;

import io.beep.core.domain.user.Email;

public interface MailPort {
  void sendVerification(Email to, String locale, String token);

  void sendPasswordReset(Email to, String locale, String token);

  /** Envoyé à la place d'une confirmation quand l'adresse a déjà un compte (anti-énumération côté API). */
  void sendAlreadyRegistered(Email to, String locale);

  /** Lien de connexion à usage unique (15 min). */
  void sendMagicLink(Email to, String locale, String token);

  /** Invitation à la bêta privée envoyée par le staff. */
  void sendInvitation(Email to, String locale);

  /** Réponse à une inscription d'une adresse non invitée pendant la bêta privée (même réponse API qu'une inscription). */
  void sendInvitationRequired(Email to, String locale);
}
