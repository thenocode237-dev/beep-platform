package io.beep.core.app.port;

import io.beep.core.domain.user.Email;

public interface MailPort {
  default void sendWorkspaceMessage(Email to,String locale,String subject,String message,String path){throw new UnsupportedOperationException("workspace mail");}
  default void sendQuotaNotice(Email to,String locale,long links,java.time.Instant expiredAt) { throw new UnsupportedOperationException("quota notice"); }

  default void sendAccountDeletion(Email to, String locale, String token) { throw new UnsupportedOperationException("account deletion mail"); }

  void sendVerification(Email to, String locale, String token);

  void sendPasswordReset(Email to, String locale, String token);

  /** Envoyé à la place d'une confirmation quand l'adresse a déjà un compte (anti-énumération côté API). */
  void sendAlreadyRegistered(Email to, String locale);

  /** Lien de connexion à usage unique (15 min). */
  void sendMagicLink(Email to, String locale, String token);

  /** Invitation à la bêta privée envoyée par le staff. */
  void sendInvitation(Email to, String locale);

  /** Reçu de paiement avec la facture PDF en pièce jointe. */
  void sendReceipt(Email to, String locale, String invoiceNumber, String planName, long amountXaf, java.time.Instant validUntil, byte[] invoicePdf);

  /** Rappel d'échéance (J−7, J−3, J−1) d'un abonnement ou d'un essai, avec lien de renouvellement. */
  void sendRenewalReminder(Email to, String locale, String planName, boolean trial, int daysLeft, java.time.Instant dueAt);

  /** Échéance dépassée : accès complet maintenu jusqu'à `graceEnd`. */
  void sendGraceNotice(Email to, String locale, String planName, java.time.Instant graceEnd);

  /** Fin de la période de grâce (ou de l'essai) : retour au plan Starter, ressources conservées. */
  void sendPlanExpired(Email to, String locale, String planName);

  /** Réponse à une inscription d'une adresse non invitée pendant la bêta privée (même réponse API qu'une inscription). */
  void sendInvitationRequired(Email to, String locale);
}
