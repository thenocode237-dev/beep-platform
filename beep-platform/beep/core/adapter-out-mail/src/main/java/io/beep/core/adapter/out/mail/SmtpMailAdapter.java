package io.beep.core.adapter.out.mail;

import io.beep.core.app.port.MailPort;
import io.beep.core.domain.user.Email;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** E-mails transactionnels via SMTP (Mailpit en dev, fournisseur transactionnel en production). */
public class SmtpMailAdapter implements MailPort {
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
    send(to, en ? "Confirm your Beep email address" : "Confirmez votre adresse e-mail Beep",
        en ? "Welcome to Beep!" : "Bienvenue sur Beep !",
        en ? "Confirm your address to activate your account. The link is valid for 24 hours." : "Confirmez votre adresse pour activer votre compte. Le lien est valable 24 heures.",
        en ? "Confirm my address" : "Confirmer mon adresse", link,
        en ? "If you did not create an account, you can ignore this email." : "Si vous n'avez pas créé de compte, ignorez simplement ce message.");
  }

  @Override
  public void sendPasswordReset(Email to, String locale, String token) {
    String link = link(locale, "/reset-password", token);
    boolean en = "en".equals(locale);
    send(to, en ? "Reset your Beep password" : "Réinitialisez votre mot de passe Beep",
        en ? "Password reset" : "Réinitialisation du mot de passe",
        en ? "Use the button below to choose a new password. The link is valid for 1 hour and works once." : "Utilisez le bouton ci-dessous pour choisir un nouveau mot de passe. Le lien est valable 1 heure et ne fonctionne qu'une fois.",
        en ? "Choose a new password" : "Choisir un nouveau mot de passe", link,
        en ? "If you did not request this, you can ignore this email: your password is unchanged." : "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe reste inchangé.");
  }

  @Override
  public void sendAlreadyRegistered(Email to, String locale) {
    boolean en = "en".equals(locale);
    send(to, en ? "You already have a Beep account" : "Vous avez déjà un compte Beep",
        en ? "You already have an account" : "Vous avez déjà un compte",
        en ? "Someone tried to sign up with this address. You can log in, or reset your password if you forgot it." : "Quelqu'un a tenté de s'inscrire avec cette adresse. Connectez-vous, ou réinitialisez votre mot de passe si vous l'avez oublié.",
        en ? "Log in" : "Se connecter", publicUrl + ("en".equals(locale) ? "/en" : "") + "/login",
        en ? "If this wasn't you, no action is needed." : "Si ce n'était pas vous, aucune action n'est nécessaire.");
  }

  @Override
  public void sendMagicLink(Email to, String locale, String token) {
    boolean en = "en".equals(locale);
    send(to, en ? "Your Beep sign-in link" : "Votre lien de connexion Beep",
        en ? "Sign in to Beep" : "Connexion à Beep",
        en ? "Use the button below to sign in. The link is valid for 15 minutes and works once." : "Utilisez le bouton ci-dessous pour vous connecter. Le lien est valable 15 minutes et ne fonctionne qu'une fois.",
        en ? "Sign in" : "Me connecter", link(locale, "/login/magic", token),
        en ? "If you did not request this, you can ignore this email." : "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
  }

  @Override
  public void sendInvitation(Email to, String locale) {
    boolean en = "en".equals(locale);
    send(to, en ? "You're invited to the Beep private beta" : "Vous êtes invité(e) à la bêta privée de Beep",
        en ? "Welcome to the Beep beta" : "Bienvenue dans la bêta de Beep",
        en ? "Create your account with this email address to try short links, QR codes and bio pages. The invitation is valid for 30 days."
            : "Créez votre compte avec cette adresse e-mail pour essayer les liens courts, QR codes et pages bio. L'invitation est valable 30 jours.",
        en ? "Create my account" : "Créer mon compte", publicUrl + (en ? "/en" : "") + "/signup",
        en ? "If you weren't expecting this invitation, you can ignore this email." : "Si vous n'attendiez pas cette invitation, ignorez simplement ce message.");
  }

  @Override
  public void sendInvitationRequired(Email to, String locale) {
    boolean en = "en".equals(locale);
    send(to, en ? "Beep is in private beta" : "Beep est en bêta privée",
        en ? "Invitation required" : "Invitation nécessaire",
        en ? "Someone tried to create a Beep account with this address. During the private beta, accounts can only be created by invitation."
            : "Quelqu'un a tenté de créer un compte Beep avec cette adresse. Pendant la bêta privée, les comptes sont créés uniquement sur invitation.",
        en ? "Discover Beep" : "Découvrir Beep", publicUrl + (en ? "/en" : "/"),
        en ? "If this wasn't you, no action is needed." : "Si ce n'était pas vous, aucune action n'est nécessaire.");
  }

  private String link(String locale, String path, String token) {
    return publicUrl + ("en".equals(locale) ? "/en" : "") + path + "?token=" + token;
  }

  private void send(Email to, String subject, String title, String body, String cta, String url, String footer) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper h = new MimeMessageHelper(message, true, "UTF-8");
      h.setFrom(from);
      h.setTo(to.value());
      h.setSubject(subject);
      h.setText(title + "\n\n" + body + "\n\n" + cta + " : " + url + "\n\n" + footer, html(title, body, cta, url, footer));
      sender.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("envoi e-mail impossible", e);
    }
  }

  // HTML minimal, une colonne, bouton ≥ 44 px : lisible sur mobile et dans les clients e-mail restrictifs.
  private static String html(String title, String body, String cta, String url, String footer) {
    return """
        <!doctype html><html><body style="margin:0;background:#F8F7F2;font-family:Arial,Helvetica,sans-serif;color:#222B25">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 16px">
        <table role="presentation" width="100%%" style="max-width:480px;background:#FFFFFF;border-radius:16px;padding:24px">
        <tr><td style="font-size:22px;font-weight:700;color:#BD3516;padding-bottom:16px">beep</td></tr>
        <tr><td style="font-size:20px;font-weight:700;padding-bottom:8px">%s</td></tr>
        <tr><td style="font-size:16px;line-height:1.6;color:#58634F;padding-bottom:24px">%s</td></tr>
        <tr><td style="padding-bottom:24px"><a href="%s" style="display:inline-block;background:#BD3516;color:#FFFFFF;text-decoration:none;font-weight:700;padding:14px 24px;border-radius:999px;min-height:44px">%s</a></td></tr>
        <tr><td style="font-size:13px;line-height:1.5;color:#58634F">%s</td></tr>
        </table></td></tr></table></body></html>
        """.formatted(title, body, url, cta, footer);
  }
}
