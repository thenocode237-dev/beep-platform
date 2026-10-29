package io.beep.core.adapter.out.mail;

import io.beep.core.app.port.MailPort;
import io.beep.core.domain.user.Email;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** E-mails transactionnels via SMTP (Mailpit en dev, fournisseur transactionnel en production). */
public class SmtpMailAdapter implements MailPort {
  @Override public void sendWorkspaceMessage(Email to,String locale,String subject,String message,String path){
    send(to,subject,subject,message,"en".equals(locale)?"Open BeepClick":"Ouvrir BeepClick",publicUrl+("en".equals(locale)?"/en":"")+path,"BeepClick · Workspace");
  }
  private final JavaMailSender sender;
  private final String from;
  private final String publicUrl;

  public SmtpMailAdapter(JavaMailSender sender, String from, String publicUrl) {
    this.sender = sender;
    this.from = from;
    this.publicUrl = publicUrl.replaceAll("/+$", "");
  }

  @Override
  public void sendVerification(Email to, String locale, String token) {
    String link = link(locale, "/verify-email", token);
    boolean en = "en".equals(locale);
    send(to, en ? "Confirm your BeepClick email address" : "Confirmez votre adresse e-mail BeepClick",
        en ? "Welcome to BeepClick!" : "Bienvenue sur BeepClick !",
        en ? "Confirm your address to activate your account. The link is valid for 24 hours." : "Confirmez votre adresse pour activer votre compte. Le lien est valable 24 heures.",
        en ? "Confirm my address" : "Confirmer mon adresse", link,
        en ? "If you did not create an account, you can ignore this email." : "Si vous n'avez pas créé de compte, ignorez simplement ce message.");
  }

  @Override
  public void sendPasswordReset(Email to, String locale, String token) {
    String link = link(locale, "/reset-password", token);
    boolean en = "en".equals(locale);
    send(to, en ? "Reset your BeepClick password" : "Réinitialisez votre mot de passe BeepClick",
        en ? "Password reset" : "Réinitialisation du mot de passe",
        en ? "Use the button below to choose a new password. The link is valid for 1 hour and works once." : "Utilisez le bouton ci-dessous pour choisir un nouveau mot de passe. Le lien est valable 1 heure et ne fonctionne qu'une fois.",
        en ? "Choose a new password" : "Choisir un nouveau mot de passe", link,
        en ? "If you did not request this, you can ignore this email: your password is unchanged." : "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe reste inchangé.");
  }

  @Override
  public void sendAlreadyRegistered(Email to, String locale) {
    boolean en = "en".equals(locale);
    send(to, en ? "You already have a BeepClick account" : "Vous avez déjà un compte BeepClick",
        en ? "You already have an account" : "Vous avez déjà un compte",
        en ? "Someone tried to sign up with this address. You can log in, or reset your password if you forgot it." : "Quelqu'un a tenté de s'inscrire avec cette adresse. Connectez-vous, ou réinitialisez votre mot de passe si vous l'avez oublié.",
        en ? "Log in" : "Se connecter", publicUrl + ("en".equals(locale) ? "/en" : "") + "/login",
        en ? "If this wasn't you, no action is needed." : "Si ce n'était pas vous, aucune action n'est nécessaire.");
  }

  @Override
  public void sendMagicLink(Email to, String locale, String token) {
    boolean en = "en".equals(locale);
    send(to, en ? "Your BeepClick sign-in link" : "Votre lien de connexion BeepClick",
        en ? "Sign in to BeepClick" : "Connexion à BeepClick",
        en ? "Use the button below to sign in. The link is valid for 15 minutes and works once." : "Utilisez le bouton ci-dessous pour vous connecter. Le lien est valable 15 minutes et ne fonctionne qu'une fois.",
        en ? "Sign in" : "Me connecter", link(locale, "/login/magic", token),
        en ? "If you did not request this, you can ignore this email." : "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
  }

  @Override
  public void sendReceipt(Email to, String locale, String invoiceNumber, String planName, long amountXaf, Instant validUntil, byte[] invoicePdf) {
    boolean en = "en".equals(locale);
    send(to, en ? "Your BeepClick receipt " + invoiceNumber : "Votre reçu BeepClick " + invoiceNumber,
        en ? "Payment received" : "Paiement reçu",
        en ? "Thank you! Your " + planName + " plan is active until " + date(validUntil) + ". Amount paid: " + money(amountXaf) + " FCFA. Your invoice " + invoiceNumber + " is attached."
            : "Merci ! Votre offre " + planName + " est active jusqu'au " + date(validUntil) + ". Montant payé : " + money(amountXaf) + " FCFA. Votre facture " + invoiceNumber + " est jointe.",
        en ? "View my subscription" : "Voir mon abonnement", publicUrl + (en ? "/en" : "") + "/app/billing",
        en ? "Keep this email: the invoice is also available in your account." : "Conservez ce message : la facture est aussi disponible dans votre compte.",
        invoiceNumber + ".pdf", invoicePdf);
  }

  @Override
  public void sendRenewalReminder(Email to, String locale, String planName, boolean trial, int daysLeft, Instant dueAt) {
    boolean en = "en".equals(locale);
    String when = daysLeft == 1 ? (en ? "tomorrow" : "demain") : (en ? "in " + daysLeft + " days" : "dans " + daysLeft + " jours");
    send(to, trial ? (en ? "Your BeepClick Elite trial ends " + when : "Votre essai Elite se termine " + when) : (en ? "Your BeepClick " + planName + " plan expires " + when : "Votre offre BeepClick " + planName + " expire " + when),
        trial ? (en ? "Your trial is ending" : "Votre essai se termine") : (en ? "Time to renew" : "C'est bientôt l'échéance"),
        trial ? (en ? "Your free Elite trial ends on " + date(dueAt) + ". Choose a plan to keep your Pro features; otherwise you return to Starter and keep all your links."
            : "Votre essai gratuit Elite se termine le " + date(dueAt) + ". Choisissez une offre pour conserver vos fonctions Pro ; sinon vous repassez en Starter et gardez tous vos liens.")
            : (en ? "Your " + planName + " plan is paid until " + date(dueAt) + ". Renew with Mobile Money. Excess links may pause 30 days after an unpaid expiry and be deleted after 90 days, with advance notice."
            : "Votre offre " + planName + " est payée jusqu'au " + date(dueAt) + ". Renouvelez avec Mobile Money. Les liens en excès peuvent être mis en pause 30 jours après une échéance impayée, puis supprimés après 90 jours, avec préavis."),
        en ? "Renew now" : "Renouveler maintenant", publicUrl + (en ? "/en" : "") + "/app/billing",
        en ? "You receive this reminder because of your subscription." : "Vous recevez ce rappel au titre de votre abonnement.", null, null);
  }

  @Override
  public void sendGraceNotice(Email to, String locale, String planName, Instant graceEnd) {
    boolean en = "en".equals(locale);
    send(to, en ? "Your BeepClick plan has expired — 7 days of grace" : "Votre offre BeepClick a expiré — 7 jours de grâce",
        en ? "Your plan has expired" : "Votre offre a expiré",
        en ? "Your " + planName + " plan was not renewed. You keep full access until " + date(graceEnd) + ", then return to Starter. Excess links may pause at day 30 and be deleted from day 90 after the unpaid expiry, with advance notice."
            : "Votre offre " + planName + " n'a pas été renouvelée. Vous gardez l'accès complet jusqu'au " + date(graceEnd) + ", puis repassez en Starter. Les liens en excès peuvent être mis en pause à J+30 et supprimés à partir de J+90 après l'échéance impayée, avec préavis.",
        en ? "Renew now" : "Renouveler maintenant", publicUrl + (en ? "/en" : "") + "/app/billing",
        en ? "Nothing is deleted when a plan ends." : "Rien n'est supprimé à la fin d'une offre.", null, null);
  }

  @Override
  public void sendPlanExpired(Email to, String locale, String planName) {
    boolean en = "en".equals(locale);
    send(to, en ? "Your account is back on Starter" : "Votre compte est repassé en Starter",
        en ? "Back on Starter" : "Retour au plan Starter",
        en ? "Your " + planName + " plan has ended. Resources above Starter quotas are read-only. Excess links may pause 30 days after the unpaid expiry and be deleted from day 90, with at least seven days of notice. Renew or export your data."
            : "Votre offre " + planName + " est terminée. Les ressources au-delà des quotas Starter passent en lecture seule. Les liens en excès peuvent être mis en pause 30 jours après l'échéance impayée et supprimés à partir de J+90, avec au moins sept jours de préavis. Renouvelez ou exportez vos données.",
        en ? "Choose a plan" : "Choisir une offre", publicUrl + (en ? "/en" : "") + "/app/billing",
        en ? "You can renew at any time." : "Vous pouvez renouveler à tout moment.", null, null);
  }

  @Override
  public void sendAccountDeletion(Email to, String locale, String token) {
    boolean en = "en".equals(locale);
    send(to, en ? "Confirm deletion of your BeepClick account" : "Confirmer la suppression de votre compte BeepClick",
      en ? "Account deletion" : "Suppression du compte",
      en ? "You requested account deletion. Open this link within 15 minutes and confirm. Nothing is deleted by opening it."
         : "Vous avez demandé la suppression de votre compte. Ouvrez ce lien dans les 15 minutes et confirmez. Ouvrir le lien ne supprime rien.",
      en ? "Review deletion" : "Vérifier la suppression", publicUrl+(en?"/en":"")+"/app/settings#erase="+token,
      en ? "If you did not request this, ignore this email." : "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.");
  }

  @Override
  public void sendQuotaNotice(Email to,String locale,long links,Instant expiredAt) {
    boolean en="en".equals(locale);
    send(to,en ? "Your excess BeepClick links" : "Vos liens BeepClick en excès",
      en ? "Keep your links" : "Conservez vos liens",
      en ? links+" links exceed your current plan. They pause on "+date(expiredAt.plus(java.time.Duration.ofDays(30)))+" and are deleted from "+date(expiredAt.plus(java.time.Duration.ofDays(90)))+". Renew or export your account."
         : links+" liens dépassent votre offre actuelle. Mise en pause le "+date(expiredAt.plus(java.time.Duration.ofDays(30)))+", suppression à partir du "+date(expiredAt.plus(java.time.Duration.ofDays(90)))+". Renouvelez ou exportez votre compte.",
      en ? "Manage subscription" : "Gérer mon abonnement",publicUrl+(en ? "/en" : "")+"/app/billing",
      en ? "Deletion is delayed until at least 7 days after this notice." : "La suppression intervient au moins 7 jours après cet avertissement.");
  }

  private String link(String locale, String path, String token) {
    return publicUrl + ("en".equals(locale) ? "/en" : "") + path + "?token=" + token;
  }

  private void send(Email to, String subject, String title, String body, String cta, String url, String footer) {
    send(to, subject, title, body, cta, url, footer, null, null);
  }

  private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.ROOT).withZone(java.time.ZoneOffset.UTC);

  private static String date(Instant at) {
    return DAY.format(at);
  }

  private static String money(long xaf) {
    return String.format(java.util.Locale.FRANCE, "%,d", xaf).replace(' ', ' ').replace(' ', ' ');
  }

  private void send(Email to, String subject, String title, String body, String cta, String url, String footer, String attachmentName, byte[] attachment) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper h = new MimeMessageHelper(message, true, "UTF-8");
      h.setFrom(from);
      h.setTo(to.value());
      h.setSubject(subject);
      h.setText(title + "\n\n" + body + "\n\n" + cta + " : " + url + "\n\n" + footer, html(title, body, cta, url, footer));
      if (attachment != null && attachmentName != null) h.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment), "application/pdf");
      sender.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("envoi e-mail impossible", e);
    }
  }

  // HTML minimal, une colonne, bouton ≥ 44 px : lisible sur mobile et dans les clients e-mail restrictifs.
  private static String html(String title, String body, String cta, String url, String footer) {
    return """
        <!doctype html><html><body style="margin:0;background:#FFFFFF;font-family:Arial,Helvetica,sans-serif;color:#192B46">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 16px">
        <table role="presentation" width="100%%" style="max-width:480px;background:#FFFFFF;border-radius:16px;padding:24px">
        <tr><td style="font-size:22px;font-weight:700;color:#2457D6;padding-bottom:16px">beep</td></tr>
        <tr><td style="font-size:20px;font-weight:700;padding-bottom:8px">%s</td></tr>
        <tr><td style="font-size:16px;line-height:1.6;color:#4C607D;padding-bottom:24px">%s</td></tr>
        <tr><td style="padding-bottom:24px"><a href="%s" style="display:inline-block;background:#2457D6;color:#FFFFFF;text-decoration:none;font-weight:700;padding:14px 24px;border-radius:999px;min-height:44px">%s</a></td></tr>
        <tr><td style="font-size:13px;line-height:1.5;color:#4C607D">%s</td></tr>
        </table></td></tr></table></body></html>
        """.formatted(title, body, url, cta, footer);
  }
}
