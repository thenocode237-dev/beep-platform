package io.beep.bo.bootstrap;

import io.beep.bo.adapter.in.http.HttpSettings;
import io.beep.bo.adapter.out.coregateway.CoreGatewayAdapter;
import io.beep.bo.adapter.out.postgres.PostgresAuditAdapter;
import io.beep.bo.adapter.out.postgres.PostgresDatabaseStatsAdapter;
import io.beep.bo.adapter.out.postgres.PostgresPlanDraftAdapter;
import io.beep.bo.app.billing.BillingUseCase;
import io.beep.bo.app.catalog.CatalogUseCase;
import io.beep.bo.app.domains.DomainsUseCase;
import io.beep.bo.app.plans.PlanDraftsUseCase;
import io.beep.bo.app.port.PlanDraftRepository;
import io.beep.bo.adapter.out.postgres.PostgresStaffAdapter;
import io.beep.bo.adapter.out.postgres.PostgresStaffSessionAdapter;
import io.beep.bo.adapter.out.security.AesGcmSecretCipherAdapter;
import io.beep.bo.adapter.out.security.Argon2PasswordHasherAdapter;
import io.beep.bo.adapter.out.security.InMemoryRateLimiterAdapter;
import io.beep.bo.adapter.out.security.SecureTokenAdapter;
import io.beep.bo.app.audit.AuditLogUseCase;
import io.beep.bo.app.audit.VerifyAuditChainUseCase;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.invitations.InvitationsUseCase;
import io.beep.bo.app.links.LinksModerationUseCase;
import io.beep.bo.app.ops.OperationsUseCase;
import io.beep.bo.app.plans.EntitlementsUseCase;
import io.beep.bo.app.port.DatabaseStatsPort;
import io.beep.bo.app.users.UserAdminUseCase;
import io.beep.bo.app.auth.StaffAuthenticateUseCase;
import io.beep.bo.app.auth.StaffLoginUseCase;
import io.beep.bo.app.auth.StaffLogoutUseCase;
import io.beep.bo.app.auth.StaffSettings;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.port.PasswordHasherPort;
import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.app.port.StaffSessionRepository;
import io.beep.bo.app.port.TokenPort;
import io.beep.bo.app.users.ListUsersUseCase;
import java.time.Clock;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Racine de composition. Désactivée en profil `migrate` (qui ne doit exiger aucun secret applicatif). */
@Configuration
@Profile("!migrate")
class ApplicationWiring {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  StaffSettings staffSettings() {
    return StaffSettings.defaults();
  }

  @Bean
  HttpSettings httpSettings(@Value("${beep.bo.public-url}") String publicUrl, StaffSettings settings) {
    return new HttpSettings(publicUrl.startsWith("https://"), settings.sessionTtl());
  }

  @Bean
  StaffRepository staffRepository(DSLContext dsl) {
    return new PostgresStaffAdapter(dsl);
  }

  @Bean
  StaffSessionRepository staffSessionRepository(DSLContext dsl) {
    return new PostgresStaffSessionAdapter(dsl);
  }

  @Bean
  AuditPort auditPort(DSLContext dsl) {
    return new PostgresAuditAdapter(dsl);
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
  SecretCipherPort secretCipher(@Value("${beep.bo.encryption-key}") String key) {
    if (key == null || key.isBlank()) throw new IllegalStateException("BO_ENCRYPTION_KEY est obligatoire (32 octets en base64 : `openssl rand -base64 32`)");
    return new AesGcmSecretCipherAdapter(key);
  }

  @Bean
  RateLimiterPort rateLimiter(Clock clock) {
    return new InMemoryRateLimiterAdapter(clock);
  }

  @Bean
  CoreGatewayPort coreGateway(@Value("${beep.bo.core-admin-url}") String url, @Value("${beep.bo.core-admin-token}") String token) {
    if (token == null || token.isBlank()) throw new IllegalStateException("CORE_ADMIN_TOKEN est obligatoire");
    return new CoreGatewayAdapter(url, token);
  }

  @Bean
  StaffLoginUseCase staffLogin(StaffRepository staff, StaffSessionRepository sessions, PasswordHasherPort hasher, TokenPort tokens, SecretCipherPort cipher,
      RateLimiterPort limiter, AuditPort audit, Clock clock, StaffSettings settings) {
    return new StaffLoginUseCase(staff, sessions, hasher, tokens, cipher, limiter, audit, clock, settings);
  }

  @Bean
  StaffAuthenticateUseCase staffAuthenticate(StaffSessionRepository sessions, StaffRepository staff, TokenPort tokens, Clock clock, StaffSettings settings) {
    return new StaffAuthenticateUseCase(sessions, staff, tokens, clock, settings);
  }

  @Bean
  StaffLogoutUseCase staffLogout(StaffSessionRepository sessions, TokenPort tokens, AuditPort audit, Clock clock) {
    return new StaffLogoutUseCase(sessions, tokens, audit, clock);
  }

  @Bean
  ListUsersUseCase listUsers(CoreGatewayPort core, AuditPort audit, Clock clock) {
    return new ListUsersUseCase(core, audit, clock);
  }

  @Bean
  DatabaseStatsPort databaseStats(DSLContext dsl) {
    return new PostgresDatabaseStatsAdapter(dsl);
  }

  @Bean
  StepUp stepUp(SecretCipherPort cipher, RateLimiterPort limiter, Clock clock, StaffSettings settings) {
    return new StepUp(cipher, limiter, clock, settings);
  }

  @Bean
  UserAdminUseCase userAdmin(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new UserAdminUseCase(core, audit, stepUp, clock);
  }

  @Bean
  LinksModerationUseCase linksModeration(CoreGatewayPort core, AuditPort audit, Clock clock) {
    return new LinksModerationUseCase(core, audit, clock);
  }

  @Bean
  EntitlementsUseCase entitlements(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new EntitlementsUseCase(core, audit, stepUp, clock);
  }

  @Bean
  PlanDraftRepository planDraftRepository(DSLContext dsl) {
    return new PostgresPlanDraftAdapter(dsl);
  }

  @Bean
  BillingUseCase billing(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new BillingUseCase(core, audit, stepUp, clock);
  }

  @Bean
  CatalogUseCase catalogUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new CatalogUseCase(core, audit, stepUp, clock);
  }

  @Bean
  DomainsUseCase domainsUseCase(CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new DomainsUseCase(core, audit, stepUp, clock);
  }

  @Bean
  PlanDraftsUseCase planDrafts(PlanDraftRepository drafts, CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    return new PlanDraftsUseCase(drafts, core, audit, stepUp, clock);
  }

  @Bean
  OperationsUseCase operations(CoreGatewayPort core, DatabaseStatsPort database) {
    return new OperationsUseCase(core, database);
  }

  @Bean
  AuditLogUseCase auditLog(AuditPort audit, Clock clock) {
    return new AuditLogUseCase(audit, clock);
  }

  @Bean
  InvitationsUseCase invitations(CoreGatewayPort core, AuditPort audit, Clock clock) {
    return new InvitationsUseCase(core, audit, clock);
  }

  @Bean
  VerifyAuditChainUseCase verifyAuditChain(AuditPort audit) {
    return new VerifyAuditChainUseCase(audit);
  }

  @Bean
  io.beep.bo.app.trust.TrustConsoleUseCase trustConsole(CoreGatewayPort core,AuditPort audit,StepUp stepUp,Clock clock) {return new io.beep.bo.app.trust.TrustConsoleUseCase(core,audit,stepUp,clock);}

}
