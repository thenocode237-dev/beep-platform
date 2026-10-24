package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.auth.AuthOutcome;
import io.beep.core.app.auth.MagicLinkUseCase;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.port.OAuthPort;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Bêta sur invitation, lien magique, OAuth, second facteur, suspension. */
class AccessTest {
  Platform p;

  @BeforeEach
  void setUp() {
    p = new Platform();
  }

  @Test
  void signupWithoutInvitationSendsInvitationRequiredAndCreatesNothing() {
    p.signUp().execute(new SignUpUseCase.Command("eve@example.com", "correct-horse-battery", "fr", "ip"));
    assertThat(p.mail.sent).containsExactly("invite-required:eve@example.com");
    assertThat(p.users.byId).isEmpty();
  }

  @Test
  void invitationAllowsSignupOnceAndAlreadyRegisteredAddressesCannotBeInvited() {
    p.adminInvitations.invite("Eve@Example.com", "fr", "staff-1");
    assertThat(p.mail.sent).containsExactly("invite:eve@example.com");
    p.signUp().execute(new SignUpUseCase.Command("eve@example.com", "correct-horse-battery", "fr", "ip"));
    assertThat(p.users.findByEmail(new Email("eve@example.com"))).isPresent();
    assertThat(p.invitations.findByEmail(new Email("eve@example.com")).orElseThrow().usedAt()).isNotNull();
    p.verifiedUser("ada@example.com");
    assertThatThrownBy(() -> p.adminInvitations.invite("ada@example.com", "fr", "s")).hasMessage("already_registered");
  }

  @Test
  void magicLinkSignsUpAnInvitedAddressAndProvesIt() {
    p.adminInvitations.invite("eve@example.com", "en", "staff-1");
    p.magicLink().request(new MagicLinkUseCase.Request("eve@example.com", "en", "ip"));
    assertThat(p.mail.sent).contains("magic:eve@example.com");
    AuthOutcome out = p.magicLink().consume(p.mail.lastToken);
    assertThat(out.mfaRequired()).isFalse();
    assertThat(out.sessionToken()).isNotBlank();
    User eve = p.users.findByEmail(new Email("eve@example.com")).orElseThrow();
    assertThat(eve.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(eve.passwordHash()).isNull();
    String used = p.mail.lastToken;
    assertThatThrownBy(() -> p.magicLink().consume(used)).hasMessage("invalid_token");
  }

  @Test
  void magicLinkForAnUninvitedAddressRevealsNothingAndCreatesNothing() {
    p.magicLink().request(new MagicLinkUseCase.Request("eve@example.com", "fr", "ip"));
    p.magicLink().request(new MagicLinkUseCase.Request("not an email", "fr", "ip"));
    assertThat(p.users.byId).isEmpty();
    assertThat(p.mail.sent).containsExactly("invite-required:eve@example.com");
  }

  @Test
  void totpStaysMandatoryAfterAMagicLink() {
    User ada = p.verifiedUser("ada@example.com");
    String secret = Totp.newSecret();
    p.users.update(ada.withTotp(p.cipher.encrypt(secret), true));
    p.magicLink().request(new MagicLinkUseCase.Request("ada@example.com", "fr", "ip"));
    AuthOutcome out = p.magicLink().consume(p.mail.lastToken);
    assertThat(out.mfaRequired()).isTrue();
    assertThat(out.sessionToken()).isNull();
    assertThatThrownBy(() -> p.mfa().execute(out.mfaToken(), "000000")).hasMessage("invalid_totp");
    var session = p.mfa().execute(out.mfaToken(), Totp.code(secret, p.now)); // un mauvais code ne consomme pas le jeton
    assertThat(session.sessionToken()).isNotBlank();
    assertThatThrownBy(() -> p.mfa().execute(out.mfaToken(), Totp.code(secret, p.now))).hasMessage("invalid_token");
  }

  @Test
  void oauthUsesPkceAndRequiresAVerifiedEmailAndAnInvitationForNewAccounts() {
    var start = p.oauthUseCase().start(OAuthPort.Provider.GOOGLE);
    assertThat(start.authorizationUrl()).contains("state=" + start.state()).contains("code_challenge=").doesNotContain(start.codeVerifier());
    assertThatThrownBy(() -> p.oauthUseCase().start(OAuthPort.Provider.GITHUB)).hasMessage("not_found"); // non configuré

    assertThatThrownBy(() -> p.oauthUseCase().complete(OAuthPort.Provider.GOOGLE, "code", start.codeVerifier(), "fr")).hasMessage("invite_required");
    p.adminInvitations.invite("ada@example.com", "fr", "s");
    AuthOutcome out = p.oauthUseCase().complete(OAuthPort.Provider.GOOGLE, "code", start.codeVerifier(), "fr");
    assertThat(out.sessionToken()).isNotBlank();
    assertThat(p.oauth.lastVerifier).isEqualTo(start.codeVerifier());
    assertThat(p.users.findByEmail(new Email("ada@example.com")).orElseThrow().canLogin()).isTrue();

    p.oauth.next = new OAuthPort.Identity("x@example.com", false, "s2");
    assertThatThrownBy(() -> p.oauthUseCase().complete(OAuthPort.Provider.GOOGLE, "code", "v", "fr")).hasMessage("oauth_email_unverified");
    assertThatThrownBy(() -> p.oauthUseCase().complete(OAuthPort.Provider.GOOGLE, "bad", "v", "fr")).hasMessage("oauth_failed");
  }

  @Test
  void suspensionRevokesSessionsAndReactivationRestoresTheAccount() {
    User ada = p.verifiedUser("ada@example.com");
    p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    p.issuer.issue(ada.id());
    assertThat(p.sessions.byHash).hasSize(1);
    assertThat(p.adminUsers.setStatus(ada.id(), "suspended", "spam massif").status()).isEqualTo(UserStatus.SUSPENDED);
    assertThat(p.sessions.byHash).isEmpty();
    assertThatThrownBy(() -> p.adminUsers.setStatus(ada.id(), "suspended", "encore")).hasMessage("status_unchanged");
    assertThat(p.adminUsers.setStatus(ada.id(), "active", "erreur de modération").status()).isEqualTo(UserStatus.ACTIVE);
    var detail = p.adminUsers.detail(ada.id());
    assertThat(detail.workspace().suspended()).isFalse();
    assertThat(detail.usage()).hasSize(3);
    assertThat(detail.usage().get(0).used()).isEqualTo(1);
    assertThat(detail.entitlements()).containsEntry("links.stock_max", 5L);
  }
}
