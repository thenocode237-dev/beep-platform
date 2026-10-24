package io.beep.core.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Totp;
import io.beep.core.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthFlowTest {
  static final String PASSWORD = "correct-horse-battery";

  Fakes.Users users;
  Fakes.Sessions sessions;
  Fakes.Tokens tokens;
  Fakes.Mail mail;
  Fakes.Limiter limiter;
  Fakes.TokenGen tokenGen;
  Fakes.Cipher cipher;
  AuthSettings settings = AuthSettings.defaults();
  SignUpUseCase signUp;
  VerifyEmailUseCase verify;
  LoginUseCase login;
  AuthenticateUseCase authenticate;
  RequestPasswordResetUseCase requestReset;
  ResetPasswordUseCase reset;
  SetupTotpUseCase setupTotp;
  EnableTotpUseCase enableTotp;

  @BeforeEach
  void setUp() {
    users = new Fakes.Users();
    sessions = new Fakes.Sessions();
    tokens = new Fakes.Tokens();
    mail = new Fakes.Mail();
    limiter = new Fakes.Limiter();
    tokenGen = new Fakes.TokenGen();
    cipher = new Fakes.Cipher();
    var hasher = new Fakes.Hasher();
    var tx = new Fakes.Tx();
    signUp = new SignUpUseCase(users, tokens, hasher, tokenGen, mail, limiter, tx, Fakes.CLOCK, settings);
    verify = new VerifyEmailUseCase(users, tokens, tokenGen, tx, Fakes.CLOCK);
    login = new LoginUseCase(users, sessions, hasher, tokenGen, cipher, limiter, Fakes.CLOCK, settings);
    authenticate = new AuthenticateUseCase(sessions, users, tokenGen, Fakes.CLOCK);
    requestReset = new RequestPasswordResetUseCase(users, tokens, tokenGen, mail, limiter, tx, Fakes.CLOCK, settings);
    reset = new ResetPasswordUseCase(users, tokens, sessions, hasher, tokenGen, tx, Fakes.CLOCK);
    setupTotp = new SetupTotpUseCase(users, cipher, settings);
    enableTotp = new EnableTotpUseCase(users, cipher, Fakes.CLOCK);
  }

  private User registerAndVerify(String email) {
    signUp.execute(new SignUpUseCase.Command(email, PASSWORD, "fr", "ip1"));
    verify.execute(mail.lastToken);
    return users.findByEmail(new io.beep.core.domain.user.Email(email)).orElseThrow();
  }

  private LoginUseCase.Command creds(String email, String pwd, String totp) {
    return new LoginUseCase.Command(email, pwd, totp, "ip1");
  }

  @Test
  void signUpSendsVerificationAndStoresOnlyAHash() {
    signUp.execute(new SignUpUseCase.Command("Ada@Example.com", PASSWORD, "en", "ip1"));
    assertThat(mail.sent).containsExactly("verify:ada@example.com");
    var user = users.byId.values().iterator().next();
    assertThat(user.passwordHash()).isNotEqualTo(PASSWORD);
    assertThat(user.canLogin()).isFalse();
    assertThat(tokens.byHash.keySet()).noneMatch(k -> k.equals(mail.lastToken)); // jamais le jeton en clair
  }

  @Test
  void signUpRejectsWeakPassword() {
    assertThatThrownBy(() -> signUp.execute(new SignUpUseCase.Command("a@b.co", "short", "fr", "ip1")))
        .isInstanceOf(DomainException.class).hasMessage("weak_password");
  }

  @Test
  void signUpDoesNotRevealExistingAccounts() {
    registerAndVerify("ada@example.com");
    mail.sent.clear();
    signUp.execute(new SignUpUseCase.Command("ada@example.com", "another-long-password", "fr", "ip1")); // aucune exception
    assertThat(mail.sent).containsExactly("already:ada@example.com");
    assertThat(users.byId).hasSize(1);
  }

  @Test
  void loginRequiresVerifiedEmail() {
    signUp.execute(new SignUpUseCase.Command("ada@example.com", PASSWORD, "fr", "ip1"));
    assertThatThrownBy(() -> login.execute(creds("ada@example.com", PASSWORD, null)))
        .isInstanceOf(DomainException.class).hasMessage("email_not_verified");
  }

  @Test
  void loginCreatesASessionThatAuthenticates() {
    registerAndVerify("ada@example.com");
    var result = login.execute(creds("ada@example.com", PASSWORD, null));
    assertThat(authenticate.execute(result.sessionToken())).get().extracting(u -> u.email().value()).isEqualTo("ada@example.com");
    assertThat(authenticate.execute("forged")).isEmpty();
  }

  @Test
  void wrongPasswordAndUnknownAccountLookIdentical() {
    registerAndVerify("ada@example.com");
    assertThatThrownBy(() -> login.execute(creds("ada@example.com", "wrong-password-123", null))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(creds("ghost@example.com", PASSWORD, null))).hasMessage("invalid_credentials");
    assertThatThrownBy(() -> login.execute(creds("not-an-email", PASSWORD, null))).hasMessage("invalid_credentials");
  }

  @Test
  void loginIsThrottledAfterFiveAttempts() {
    registerAndVerify("ada@example.com");
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> login.execute(creds("ada@example.com", "wrong-password-123", null))).hasMessage("invalid_credentials");
    }
    assertThatThrownBy(() -> login.execute(creds("ada@example.com", PASSWORD, null))).hasMessage("rate_limited");
  }

  @Test
  void verificationTokenWorksOnlyOnce() {
    signUp.execute(new SignUpUseCase.Command("ada@example.com", PASSWORD, "fr", "ip1"));
    String token = mail.lastToken;
    verify.execute(token);
    assertThatThrownBy(() -> verify.execute(token)).hasMessage("invalid_token");
  }

  @Test
  void totpIsEnforcedOnceEnabled() {
    User user = registerAndVerify("ada@example.com");
    var setup = setupTotp.execute(user);
    User withSecret = users.findById(user.id()).orElseThrow();
    String code = Totp.code(setup.secret(), Fakes.CLOCK.instant());
    assertThatThrownBy(() -> enableTotp.execute(withSecret, "000000")).hasMessage("invalid_totp");
    enableTotp.execute(withSecret, code);

    assertThatThrownBy(() -> login.execute(creds("ada@example.com", PASSWORD, null))).hasMessage("totp_required");
    assertThatThrownBy(() -> login.execute(creds("ada@example.com", PASSWORD, "000000"))).hasMessage("invalid_totp");
    assertThat(login.execute(creds("ada@example.com", PASSWORD, code)).sessionToken()).isNotBlank();
  }

  @Test
  void passwordResetRevokesSessionsAndConsumesTheToken() {
    registerAndVerify("ada@example.com");
    var session = login.execute(creds("ada@example.com", PASSWORD, null));

    requestReset.execute(new RequestPasswordResetUseCase.Command("ada@example.com", "ip1"));
    String token = mail.lastToken;
    reset.execute(token, "a-brand-new-password");

    assertThat(authenticate.execute(session.sessionToken())).isEmpty();
    assertThat(login.execute(creds("ada@example.com", "a-brand-new-password", null)).sessionToken()).isNotBlank();
    assertThatThrownBy(() -> reset.execute(token, "yet-another-password-1")).hasMessage("invalid_token");
  }

  @Test
  void passwordResetForUnknownAddressSendsNothingAndSaysNothing() {
    requestReset.execute(new RequestPasswordResetUseCase.Command("ghost@example.com", "ip1"));
    assertThat(mail.sent).isEmpty();
  }
}
