package io.beep.core.bootstrap;

import io.beep.core.adapter.in.admin.AdminTokenFilter;
import io.beep.core.adapter.in.http.HttpSettings;
import io.beep.core.adapter.out.mail.SmtpMailAdapter;
import io.beep.core.adapter.out.postgres.PostgresOneTimeTokenAdapter;
import io.beep.core.adapter.out.postgres.PostgresSessionAdapter;
import io.beep.core.adapter.out.postgres.PostgresUserAdapter;
import io.beep.core.adapter.out.postgres.SpringTransactionAdapter;
import io.beep.core.adapter.out.security.AesGcmSecretCipherAdapter;
import io.beep.core.adapter.out.security.Argon2PasswordHasherAdapter;
import io.beep.core.adapter.out.security.InMemoryRateLimiterAdapter;
import io.beep.core.adapter.out.security.SecureTokenAdapter;
import io.beep.core.app.admin.ListUsersUseCase;
import io.beep.core.app.auth.AuthSettings;
import io.beep.core.app.auth.AuthenticateUseCase;
import io.beep.core.app.auth.EnableTotpUseCase;
import io.beep.core.app.auth.LoginUseCase;
import io.beep.core.app.auth.LogoutUseCase;
import io.beep.core.app.auth.RequestPasswordResetUseCase;
import io.beep.core.app.auth.ResetPasswordUseCase;
import io.beep.core.app.auth.SetupTotpUseCase;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.auth.VerifyEmailUseCase;
import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import java.time.Clock;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Racine de composition : seul endroit qui connaît à la fois les cas d'usage et leurs adapters.
 * Désactivée en profil `migrate` (qui n'a besoin que de Flyway et ne doit exiger aucun secret applicatif).
 */
@Configuration
@Profile("!migrate")
class ApplicationWiring {

  // ---- Réglages ----
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthSettings authSettings() {
    return AuthSettings.defaults();
  }

  @Bean
  HttpSettings httpSettings(@Value("${beep.public-url}") String publicUrl, AuthSettings auth) {
    return new HttpSettings(publicUrl.startsWith("https://"), auth.sessionTtl());
  }

  // ---- Adapters sortants ----
  @Bean
  UserRepository userRepository(DSLContext dsl) {
    return new PostgresUserAdapter(dsl);
  }

  @Bean
  SessionRepository sessionRepository(DSLContext dsl) {
    return new PostgresSessionAdapter(dsl);
  }

  @Bean
  OneTimeTokenRepository oneTimeTokenRepository(DSLContext dsl) {
    return new PostgresOneTimeTokenAdapter(dsl);
  }

  @Bean
  TransactionPort transactionPort(PlatformTransactionManager manager) {
    return new SpringTransactionAdapter(manager);
  }

  @Bean
  PasswordHasherPort passwordHasher() {
    return new Argon2PasswordHasherAdapter();
  }

  @Bean
  TokenPort tokenPort() {
    return new SecureTokenAdapter();
  }

  @Bean
  SecretCipherPort secretCipher(@Value("${beep.totp-encryption-key}") String key) {
    if (key == null || key.isBlank()) throw new IllegalStateException("TOTP_ENCRYPTION_KEY est obligatoire (32 octets en base64 : `openssl rand -base64 32`)");
    return new AesGcmSecretCipherAdapter(key);
  }

  @Bean
  RateLimiterPort rateLimiter(Clock clock) {
    return new InMemoryRateLimiterAdapter(clock);
  }

  @Bean
  MailPort mailPort(JavaMailSender sender, @Value("${beep.mail-from}") String from, @Value("${beep.public-url}") String publicUrl) {
    return new SmtpMailAdapter(sender, from, publicUrl);
  }

  // ---- Cas d'usage ----
  @Bean
  SignUpUseCase signUp(UserRepository users, OneTimeTokenRepository tokens, PasswordHasherPort hasher, TokenPort tokenPort, MailPort mail,
      RateLimiterPort limiter, TransactionPort tx, Clock clock, AuthSettings settings) {
    return new SignUpUseCase(users, tokens, hasher, tokenPort, mail, limiter, tx, clock, settings);
  }

  @Bean
  VerifyEmailUseCase verifyEmail(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, TransactionPort tx, Clock clock) {
    return new VerifyEmailUseCase(users, tokens, tokenPort, tx, clock);
  }

  @Bean
  LoginUseCase login(UserRepository users, SessionRepository sessions, PasswordHasherPort hasher, TokenPort tokenPort, SecretCipherPort cipher,
      RateLimiterPort limiter, Clock clock, AuthSettings settings) {
    return new LoginUseCase(users, sessions, hasher, tokenPort, cipher, limiter, clock, settings);
  }

  @Bean
  LogoutUseCase logout(SessionRepository sessions, TokenPort tokenPort) {
    return new LogoutUseCase(sessions, tokenPort);
  }

  @Bean
  AuthenticateUseCase authenticate(SessionRepository sessions, UserRepository users, TokenPort tokenPort, Clock clock) {
    return new AuthenticateUseCase(sessions, users, tokenPort, clock);
  }

  @Bean
  RequestPasswordResetUseCase requestPasswordReset(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, MailPort mail,
      RateLimiterPort limiter, TransactionPort tx, Clock clock, AuthSettings settings) {
    return new RequestPasswordResetUseCase(users, tokens, tokenPort, mail, limiter, tx, clock, settings);
  }

  @Bean
  ResetPasswordUseCase resetPassword(UserRepository users, OneTimeTokenRepository tokens, SessionRepository sessions, PasswordHasherPort hasher,
      TokenPort tokenPort, TransactionPort tx, Clock clock) {
    return new ResetPasswordUseCase(users, tokens, sessions, hasher, tokenPort, tx, clock);
  }

  @Bean
  SetupTotpUseCase setupTotp(UserRepository users, SecretCipherPort cipher, AuthSettings settings) {
    return new SetupTotpUseCase(users, cipher, settings);
  }

  @Bean
  EnableTotpUseCase enableTotp(UserRepository users, SecretCipherPort cipher, Clock clock) {
    return new EnableTotpUseCase(users, cipher, clock);
  }

  @Bean
  ListUsersUseCase listUsers(UserRepository users) {
    return new ListUsersUseCase(users);
  }

  // ---- API admin interne : jeton de service (deux jetons acceptés pendant une rotation) ----
  @Bean
  @Profile("!worker")
  FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(@Value("${beep.admin-tokens}") List<String> tokens) {
    FilterRegistrationBean<AdminTokenFilter> reg = new FilterRegistrationBean<>(new AdminTokenFilter(tokens));
    reg.addUrlPatterns("/internal/admin/*");
    return reg;
  }
}
