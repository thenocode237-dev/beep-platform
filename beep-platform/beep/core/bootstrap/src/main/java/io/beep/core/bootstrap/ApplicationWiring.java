package io.beep.core.bootstrap;

import io.beep.core.adapter.in.admin.AdminTokenFilter;
import io.beep.core.adapter.out.dns.JndiDnsVerifierAdapter;
import io.beep.core.adapter.out.postgres.PostgresAppCatalogAdapter;
import io.beep.core.app.admin.AdminCatalogUseCase;
import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.app.redirect.DeeplinkSimulator;
import io.beep.core.app.redirect.LinkRouter;
import io.beep.core.adapter.out.postgres.CachingDomainRouterAdapter;
import io.beep.core.adapter.out.postgres.PostgresCustomDomainAdapter;
import io.beep.core.adapter.out.postgres.PostgresDomainRouterAdapter;
import io.beep.core.app.admin.AdminDomainsUseCase;
import io.beep.core.app.domains.CustomDomainsUseCase;
import io.beep.core.app.domains.TlsAllowUseCase;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.DnsVerifierPort;
import io.beep.core.app.port.DomainRouterPort;
import io.beep.core.adapter.out.billing.InvoicePdfAdapter;
import io.beep.core.adapter.out.billing.NotchPayGatewayAdapter;
import io.beep.core.adapter.out.billing.SandboxPaymentGatewayAdapter;
import io.beep.core.adapter.out.postgres.PostgresCouponAdapter;
import io.beep.core.adapter.out.postgres.PostgresInvoiceAdapter;
import io.beep.core.adapter.out.postgres.PostgresPaymentAdapter;
import io.beep.core.adapter.out.postgres.PostgresSubscriptionAdapter;
import io.beep.core.app.billing.AdminBillingUseCase;
import io.beep.core.app.billing.BillingEngine;
import io.beep.core.app.billing.BillingLifecycleUseCase;
import io.beep.core.app.billing.BillingOverviewUseCase;
import io.beep.core.app.billing.BillingSettings;
import io.beep.core.app.billing.CheckoutUseCase;
import io.beep.core.app.billing.SettlePaymentUseCase;
import io.beep.core.app.port.CouponRepository;
import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.app.port.InvoiceRepository;
import io.beep.core.app.port.PaymentGatewayPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.SubscriptionRepository;
import io.beep.core.adapter.in.http.HttpSettings;
import io.beep.core.adapter.out.geoip.MaxMindGeoIpAdapter;
import io.beep.core.adapter.out.mail.SmtpMailAdapter;
import io.beep.core.adapter.out.oauth.HttpOAuthAdapter;
import io.beep.core.adapter.out.postgres.BatchingClickSinkAdapter;
import io.beep.core.adapter.out.postgres.CacheInvalidationListener;
import io.beep.core.adapter.out.postgres.CachingSlugResolverAdapter;
import io.beep.core.adapter.out.postgres.PostgresAnalyticsAdapter;
import io.beep.core.adapter.out.postgres.PostgresBioPageAdapter;
import io.beep.core.adapter.out.postgres.PostgresClickEventAdapter;
import io.beep.core.adapter.out.postgres.PostgresInvitationAdapter;
import io.beep.core.adapter.out.postgres.PostgresJobRunAdapter;
import io.beep.core.adapter.out.postgres.PostgresLinkAdapter;
import io.beep.core.adapter.out.postgres.PostgresOneTimeTokenAdapter;
import io.beep.core.adapter.out.postgres.PostgresPlanAdapter;
import io.beep.core.adapter.out.postgres.PostgresPlatformMetricsAdapter;
import io.beep.core.adapter.out.postgres.PostgresQrCodeAdapter;
import io.beep.core.adapter.out.postgres.PostgresRollupAdapter;
import io.beep.core.adapter.out.postgres.PostgresSessionAdapter;
import io.beep.core.adapter.out.postgres.PostgresSlugRegistryAdapter;
import io.beep.core.adapter.out.postgres.PostgresSlugResolverAdapter;
import io.beep.core.adapter.out.postgres.PostgresUserAdapter;
import io.beep.core.adapter.out.postgres.PostgresVisitorSaltAdapter;
import io.beep.core.adapter.out.postgres.PostgresWorkspaceAdapter;
import io.beep.core.adapter.out.postgres.SpringTransactionAdapter;
import io.beep.core.adapter.out.qrrender.ZxingQrRendererAdapter;
import io.beep.core.adapter.out.security.AesGcmSecretCipherAdapter;
import io.beep.core.adapter.out.security.Argon2PasswordHasherAdapter;
import io.beep.core.adapter.out.security.InMemoryRateLimiterAdapter;
import io.beep.core.adapter.out.security.SecureTokenAdapter;
import io.beep.core.app.admin.AdminEntitlementsUseCase;
import io.beep.core.app.admin.AdminInvitationsUseCase;
import io.beep.core.app.admin.AdminLinksUseCase;
import io.beep.core.app.admin.AdminUsersUseCase;
import io.beep.core.app.admin.ListUsersUseCase;
import io.beep.core.app.admin.PlatformMetricsUseCase;
import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.auth.AuthSettings;
import io.beep.core.app.auth.AuthenticateUseCase;
import io.beep.core.app.auth.CompleteMfaUseCase;
import io.beep.core.app.auth.EnableTotpUseCase;
import io.beep.core.app.auth.LoginUseCase;
import io.beep.core.app.auth.LogoutUseCase;
import io.beep.core.app.auth.MagicLinkUseCase;
import io.beep.core.app.auth.OAuthUseCase;
import io.beep.core.app.auth.RequestPasswordResetUseCase;
import io.beep.core.app.auth.ResetPasswordUseCase;
import io.beep.core.app.auth.SessionIssuer;
import io.beep.core.app.auth.SetupTotpUseCase;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.auth.VerifyEmailUseCase;
import io.beep.core.app.bio.BioPagesUseCase;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.jobs.AnalyticsMaintenanceUseCase;
import io.beep.core.app.jobs.JobCatalog;
import io.beep.core.app.jobs.JobRunner;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.link.DeleteLinkUseCase;
import io.beep.core.app.link.ListLinksUseCase;
import io.beep.core.app.link.UpdateLinkUseCase;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.ClickEventRepository;
import io.beep.core.app.port.ClickSinkPort;
import io.beep.core.app.port.GeoIpPort;
import io.beep.core.app.port.InvitationRepository;
import io.beep.core.app.port.JobRunRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.OAuthPort;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.PlatformMetricsRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.port.QrRendererPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.RollupRepository;
import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.app.port.VisitorSaltRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.qr.QrCodesUseCase;
import io.beep.core.app.redirect.BioBlockClickUseCase;
import io.beep.core.app.redirect.ResolveQrUseCase;
import io.beep.core.app.redirect.ResolveSlugUseCase;
import io.beep.core.app.workspace.WorkspaceOverviewUseCase;
import io.beep.core.app.workspace.Workspaces;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
  AuthSettings authSettings(@Value("${beep.signup-mode}") String signupMode) {
    if (!signupMode.equals("invite") && !signupMode.equals("open")) throw new IllegalStateException("SIGNUP_MODE doit valoir invite ou open");
    return AuthSettings.defaults().withInviteOnly(signupMode.equals("invite"));
  }

  @Bean
  PublicSite publicSite(@Value("${beep.public-url}") String publicUrl) {
    return new PublicSite(publicUrl);
  }

  @Bean
  HttpSettings httpSettings(@Value("${beep.public-url}") String publicUrl, AuthSettings auth) {
    return new HttpSettings(publicUrl.startsWith("https://"), auth.sessionTtl());
  }

  // ---- Adapters sortants : PostgreSQL ----
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
  WorkspaceRepository workspaceRepository(DSLContext dsl) {
    return new PostgresWorkspaceAdapter(dsl);
  }

  @Bean
  PlanRepository planRepository(DSLContext dsl) {
    return new PostgresPlanAdapter(dsl);
  }

  @Bean
  LinkRepository linkRepository(DSLContext dsl) {
    return new PostgresLinkAdapter(dsl);
  }

  @Bean
  SlugRegistryPort slugRegistry(DSLContext dsl) {
    return new PostgresSlugRegistryAdapter(dsl);
  }

  @Bean
  QrCodeRepository qrCodeRepository(DSLContext dsl) {
    return new PostgresQrCodeAdapter(dsl);
  }

  @Bean
  BioPageRepository bioPageRepository(DSLContext dsl) {
    return new PostgresBioPageAdapter(dsl);
  }

  @Bean
  InvitationRepository invitationRepository(DSLContext dsl) {
    return new PostgresInvitationAdapter(dsl);
  }

  @Bean
  AnalyticsRepository analyticsRepository(DSLContext dsl) {
    return new PostgresAnalyticsAdapter(dsl);
  }

  @Bean
  RollupRepository rollupRepository(DSLContext dsl) {
    return new PostgresRollupAdapter(dsl);
  }

  @Bean
  JobRunRepository jobRunRepository(DSLContext dsl) {
    return new PostgresJobRunAdapter(dsl);
  }

  @Bean
  PlatformMetricsRepository platformMetricsRepository(DSLContext dsl) {
    return new PostgresPlatformMetricsAdapter(dsl);
  }

  @Bean
  VisitorSaltRepository visitorSaltRepository(DSLContext dsl) {
    return new PostgresVisitorSaltAdapter(dsl);
  }

  @Bean
  ClickEventRepository clickEventRepository(DSLContext dsl) {
    return new PostgresClickEventAdapter(dsl);
  }

  // ---- Chemin de redirection : file d'événements bornée, cache des slugs invalidé par LISTEN/NOTIFY ----
  @Bean(destroyMethod = "close")
  BatchingClickSinkAdapter clickSink(ClickEventRepository store, MeterRegistry registry, @Value("${beep.events.queue-capacity}") int capacity,
      @Value("${beep.events.batch-size}") int batchSize, @Value("${beep.events.flush-interval}") Duration flush) {
    BatchingClickSinkAdapter sink = new BatchingClickSinkAdapter(store, capacity, batchSize, flush).start();
    Gauge.builder("beep.events.queued", () -> sink.stats().queued()).description("Événements en attente d'écriture").register(registry);
    Gauge.builder("beep.events.dropped", () -> sink.stats().dropped()).description("Événements abandonnés (file pleine ou base indisponible)").register(registry);
    Gauge.builder("beep.events.written", () -> sink.stats().written()).description("Événements écrits").register(registry);
    return sink;
  }

  @Bean
  CachingSlugResolverAdapter slugResolver(DSLContext dsl, BioPageRepository bioPages, MeterRegistry registry, @Value("${beep.redirect.cache-size}") long size) {
    CachingSlugResolverAdapter cache = new CachingSlugResolverAdapter(new PostgresSlugResolverAdapter(dsl, bioPages), size);
    cache.caches().forEach((name, c) -> CaffeineCacheMetrics.monitor(registry, c, name));
    return cache;
  }

  /** Instances API uniquement (le worker ne sert pas de redirections). Connexion dédiée, directe. */
  @Bean(destroyMethod = "close")
  @Profile("!worker")
  CacheInvalidationListener cacheInvalidationListener(CachingSlugResolverAdapter cache, CachingDomainRouterAdapter domainRouter, PostgresAppCatalogAdapter catalog, @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String user, @Value("${spring.datasource.password}") String password) {
    return new CacheInvalidationListener(url, user, password, payload -> {
      cache.invalidate(payload);
      if (payload.startsWith("catalog:")) catalog.invalidate();
      else if (payload.startsWith("domain:")) domainRouter.invalidate(payload.substring(7));
      else if (!payload.contains(":") || payload.startsWith("ws:")) domainRouter.invalidateAll();
    }, () -> {
      cache.invalidateAll();
      domainRouter.invalidateAll();
      catalog.invalidate();
    }).start();
  }

  // ---- Autres adapters sortants ----
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

  @Bean
  QrRendererPort qrRenderer() {
    return new ZxingQrRendererAdapter();
  }

  @Bean
  GeoIpPort geoIp(@Value("${beep.geoip-db}") String path) {
    return new MaxMindGeoIpAdapter(path);
  }

  @Bean
  OAuthPort oauth(@Value("${beep.oauth.google.client-id}") String googleId, @Value("${beep.oauth.google.client-secret}") String googleSecret,
      @Value("${beep.oauth.github.client-id}") String githubId, @Value("${beep.oauth.github.client-secret}") String githubSecret) {
    return new HttpOAuthAdapter(new HttpOAuthAdapter.Client(googleId, googleSecret), new HttpOAuthAdapter.Client(githubId, githubSecret));
  }

  // ---- Cas d'usage : authentification ----
  @Bean
  SessionIssuer sessionIssuer(SessionRepository sessions, OneTimeTokenRepository tokens, TokenPort tokenPort, Clock clock, AuthSettings settings) {
    return new SessionIssuer(sessions, tokens, tokenPort, clock, settings);
  }

  @Bean
  SignUpUseCase signUp(UserRepository users, OneTimeTokenRepository tokens, InvitationRepository invitations, PasswordHasherPort hasher, TokenPort tokenPort,
      MailPort mail, RateLimiterPort limiter, TransactionPort tx, Clock clock, AuthSettings settings) {
    return new SignUpUseCase(users, tokens, invitations, hasher, tokenPort, mail, limiter, tx, clock, settings);
  }

  @Bean
  VerifyEmailUseCase verifyEmail(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, TransactionPort tx, Clock clock) {
    return new VerifyEmailUseCase(users, tokens, tokenPort, tx, clock);
  }

  @Bean
  LoginUseCase login(UserRepository users, SessionIssuer issuer, PasswordHasherPort hasher, SecretCipherPort cipher, RateLimiterPort limiter, Clock clock,
      AuthSettings settings) {
    return new LoginUseCase(users, issuer, hasher, cipher, limiter, clock, settings);
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
  MagicLinkUseCase magicLink(UserRepository users, OneTimeTokenRepository tokens, InvitationRepository invitations, TokenPort tokenPort, MailPort mail,
      RateLimiterPort limiter, TransactionPort tx, SessionIssuer issuer, Clock clock, AuthSettings settings) {
    return new MagicLinkUseCase(users, tokens, invitations, tokenPort, mail, limiter, tx, issuer, clock, settings);
  }

  @Bean
  CompleteMfaUseCase completeMfa(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, SecretCipherPort cipher, RateLimiterPort limiter,
      SessionIssuer issuer, Clock clock, AuthSettings settings) {
    return new CompleteMfaUseCase(users, tokens, tokenPort, cipher, limiter, issuer, clock, settings);
  }

  @Bean
  OAuthUseCase oauthUseCase(OAuthPort oauth, UserRepository users, InvitationRepository invitations, TokenPort tokenPort, TransactionPort tx, SessionIssuer issuer,
      PublicSite site, Clock clock, AuthSettings settings) {
    return new OAuthUseCase(oauth, users, invitations, tokenPort, tx, issuer, site, clock, settings);
  }

  // ---- Cas d'usage : espace client ----
  @Bean
  Workspaces workspaces(WorkspaceRepository workspaces, PlanRepository plans, LinkRepository links, QrCodeRepository qr, BioPageRepository bio, Clock clock) {
    return new Workspaces(workspaces, plans, links, qr, bio, clock);
  }

  @Bean
  WorkspaceOverviewUseCase workspaceOverview(Workspaces workspaces, PlanRepository plans, AnalyticsRepository analytics, Clock clock) {
    return new WorkspaceOverviewUseCase(workspaces, plans, analytics, clock);
  }

  @Bean
  CreateLinkUseCase createLink(Workspaces workspaces, WorkspaceRepository workspaceRepository, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock,
      CustomDomainRepository domains, PasswordHasherPort hasher, io.beep.core.app.trust.TrustUseCase trust) {
    return new CreateLinkUseCase(workspaces, workspaceRepository, links, tx, site, clock, domains).withHasher(hasher).withTrust(trust);
  }

  @Bean
  UpdateLinkUseCase updateLink(Workspaces workspaces, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock, PasswordHasherPort hasher, io.beep.core.app.trust.TrustUseCase trust) {
    return new UpdateLinkUseCase(workspaces, links, tx, site, clock).withHasher(hasher).withTrust(trust);
  }

  @Bean
  DeleteLinkUseCase deleteLink(Workspaces workspaces, LinkRepository links, QrCodeRepository qr, TransactionPort tx) {
    return new DeleteLinkUseCase(workspaces, links, qr, tx);
  }

  @Bean
  ListLinksUseCase listLinks(Workspaces workspaces, LinkRepository links, AnalyticsRepository analytics, Clock clock) {
    return new ListLinksUseCase(workspaces, links, analytics, clock);
  }

  @Bean
  GetStatsUseCase getStats(Workspaces workspaces, AnalyticsRepository analytics, LinkRepository links, QrCodeRepository qr, BioPageRepository bio, Clock clock) {
    return new GetStatsUseCase(workspaces, analytics, links, qr, bio, clock);
  }

  @Bean
  QrCodesUseCase qrCodes(Workspaces workspaces, WorkspaceRepository workspaceRepository, QrCodeRepository qr, LinkRepository links, AnalyticsRepository analytics,
      QrRendererPort renderer, TransactionPort tx, PublicSite site, Clock clock) {
    return new QrCodesUseCase(workspaces, workspaceRepository, qr, links, analytics, renderer, tx, site, clock);
  }

  @Bean
  BioPagesUseCase bioPages(Workspaces workspaces, WorkspaceRepository workspaceRepository, BioPageRepository pages, AnalyticsRepository analytics,
      TransactionPort tx, PublicSite site, Clock clock) {
    return new BioPagesUseCase(workspaces, workspaceRepository, pages, analytics, tx, site, clock);
  }

  // ---- Cas d'usage : redirection ----
  @Bean
  ClickRecorder clickRecorder(ClickSinkPort sink, VisitorSaltRepository salts, GeoIpPort geoIp) {
    return new ClickRecorder(sink, salts, geoIp);
  }

  @Bean
  ResolveSlugUseCase resolveSlug(SlugResolverPort resolver, ClickRecorder recorder, LinkRouter router, PasswordHasherPort hasher, RateLimiterPort limiter) {
    return new ResolveSlugUseCase(resolver, recorder, router, hasher, limiter);
  }

  @Bean
  ResolveQrUseCase resolveQr(SlugResolverPort resolver, ClickRecorder recorder, LinkRouter router, PasswordHasherPort hasher, RateLimiterPort limiter) {
    return new ResolveQrUseCase(resolver, recorder, router, hasher, limiter);
  }

  @Bean
  BioBlockClickUseCase bioBlockClick(SlugResolverPort resolver, ClickRecorder recorder) {
    return new BioBlockClickUseCase(resolver, recorder);
  }

  // ---- Cas d'usage : API admin et tâches de fond ----
  @Bean
  ListUsersUseCase listUsers(UserRepository users) {
    return new ListUsersUseCase(users);
  }

  @Bean
  AdminUsersUseCase adminUsers(UserRepository users, WorkspaceRepository workspaceRepository, Workspaces workspaces, PlanRepository plans,
      SessionRepository sessions, TransactionPort tx) {
    return new AdminUsersUseCase(users, workspaceRepository, workspaces, plans, sessions, tx);
  }

  @Bean
  AdminLinksUseCase adminLinks(LinkRepository links, TransactionPort tx, Clock clock, io.beep.core.app.trust.TrustUseCase trust) {
    return new AdminLinksUseCase(links, tx, clock).withTrust(trust);
  }

  @Bean
  AdminEntitlementsUseCase adminEntitlements(PlanRepository plans, WorkspaceRepository workspaceRepository, Workspaces workspaces, TransactionPort tx, Clock clock) {
    return new AdminEntitlementsUseCase(plans, workspaceRepository, workspaces, tx, clock);
  }

  @Bean
  AdminInvitationsUseCase adminInvitations(InvitationRepository invitations, UserRepository users, MailPort mail, TransactionPort tx, Clock clock) {
    return new AdminInvitationsUseCase(invitations, users, mail, tx, clock);
  }

  @Bean
  PlatformMetricsUseCase platformMetrics(PlatformMetricsRepository metrics, JobRunRepository jobs, ClickSinkPort sink, Clock clock) {
    return new PlatformMetricsUseCase(metrics, jobs, sink, clock);
  }

  @Bean
  JobCatalog jobCatalog(JobRunRepository runs, Clock clock) {
    return new JobCatalog(runs, clock);
  }

  @Bean
  JobRunner jobRunner(JobRunRepository runs, Clock clock) {
    return new JobRunner(runs, clock);
  }

  @Bean
  AnalyticsMaintenanceUseCase analyticsMaintenance(RollupRepository rollups, VisitorSaltRepository salts, JobRunRepository runs, Clock clock) {
    return new AnalyticsMaintenanceUseCase(rollups, salts, runs, clock);
  }

  // ---- Règles de lien et deeplinks (phase 2) ----
  @Bean
  PostgresAppCatalogAdapter appCatalog(DSLContext dsl) {
    return new PostgresAppCatalogAdapter(dsl);
  }

  @Bean
  LinkRouter linkRouter(ClickRecorder recorder, GeoIpPort geoIp, AppCatalogPort catalog) {
    return new LinkRouter(recorder, geoIp, catalog, () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());
  }

  @Bean
  DeeplinkSimulator deeplinkSimulator(AppCatalogPort catalog) {
    return new DeeplinkSimulator(catalog);
  }

  @Bean
  AdminCatalogUseCase adminCatalog(AppCatalogPort catalog, DeeplinkSimulator simulator, TransactionPort tx, Clock clock) {
    return new AdminCatalogUseCase(catalog, simulator, tx, clock);
  }

  // ---- Domaines personnalisés (phase 2) ----
  @Bean
  CustomDomainRepository customDomainRepository(DSLContext dsl) {
    return new PostgresCustomDomainAdapter(dsl);
  }

  /** Noms qui désignent le domaine de Beep : site public, plus `BEEP_MAIN_HOSTS` (ex. localhost, core). Tout autre nom est un domaine personnalisé ou inconnu. */
  @Bean
  CachingDomainRouterAdapter domainRouter(DSLContext dsl, PublicSite site, MeterRegistry registry, @Value("${beep.domains.main-hosts}") List<String> mainHosts,
      @Value("${beep.redirect.cache-size}") long size) {
    java.util.Set<String> hosts = new java.util.HashSet<>();
    hosts.add(site.host());
    mainHosts.stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).forEach(hosts::add);
    CachingDomainRouterAdapter router = new CachingDomainRouterAdapter(new PostgresDomainRouterAdapter(dsl, hosts), size);
    CaffeineCacheMetrics.monitor(registry, router.cache(), "redirect.domains");
    return router;
  }

  @Bean
  DnsVerifierPort dnsVerifier(@Value("${beep.domains.cname-target}") String cname, @Value("${beep.domains.ips}") List<String> ips, @Value("${beep.domains.resolver}") String resolver) {
    return new JndiDnsVerifierAdapter(cname, ips.stream().map(String::strip).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet()), resolver);
  }

  @Bean
  CustomDomainsUseCase customDomains(Workspaces workspaces, WorkspaceRepository workspaceRepository, CustomDomainRepository domains, DnsVerifierPort dns, TokenPort tokens,
      RateLimiterPort limiter, TransactionPort tx, PublicSite site, Clock clock) {
    return new CustomDomainsUseCase(workspaces, workspaceRepository, domains, dns, tokens, limiter, tx, site, clock);
  }

  @Bean
  TlsAllowUseCase tlsAllow(DomainRouterPort router, CustomDomainRepository domains, TransactionPort tx) {
    return new TlsAllowUseCase(router, domains, tx);
  }

  @Bean
  AdminDomainsUseCase adminDomains(CustomDomainRepository domains, CustomDomainsUseCase customer, TransactionPort tx) {
    return new AdminDomainsUseCase(domains, customer, tx);
  }

  // ---- Facturation (phase 2) ----
  @Bean
  SubscriptionRepository subscriptionRepository(DSLContext dsl) {
    return new PostgresSubscriptionAdapter(dsl);
  }

  @Bean
  PaymentRepository paymentRepository(DSLContext dsl) {
    return new PostgresPaymentAdapter(dsl);
  }

  @Bean
  InvoiceRepository invoiceRepository(DSLContext dsl) {
    return new PostgresInvoiceAdapter(dsl);
  }

  @Bean
  CouponRepository couponRepository(DSLContext dsl) {
    return new PostgresCouponAdapter(dsl);
  }

  /** `sandbox` (développement, aucun appel réseau) ou `notchpay` ; la sandbox est refusée en production. */
  @Bean
  PaymentGatewayPort paymentGateway(Environment env, @Value("${beep.billing.provider}") String provider, @Value("${beep.billing.sandbox-secret}") String sandboxSecret,
      @Value("${beep.billing.notchpay.base-url}") String baseUrl, @Value("${beep.billing.notchpay.public-key}") String publicKey,
      @Value("${beep.billing.notchpay.hash-key}") String hashKey) {
    return switch (provider) {
      case "sandbox" -> {
        if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("PAYMENT_PROVIDER=sandbox est interdit en production");
        yield new SandboxPaymentGatewayAdapter(sandboxSecret);
      }
      case "notchpay" -> {
        if (publicKey.isBlank() || hashKey.isBlank()) throw new IllegalStateException("NOTCHPAY_PUBLIC_KEY et NOTCHPAY_HASH_KEY sont obligatoires avec PAYMENT_PROVIDER=notchpay");
        yield new NotchPayGatewayAdapter(baseUrl, publicKey, hashKey);
      }
      default -> throw new IllegalStateException("PAYMENT_PROVIDER doit valoir sandbox ou notchpay");
    };
  }

  @Bean
  InvoicePdfPort invoicePdf(@Value("${beep.billing.seller.name}") String name, @Value("${beep.billing.seller.address}") String address,
      @Value("${beep.billing.seller.tax-id}") String taxId) {
    return new InvoicePdfAdapter(new InvoicePdfAdapter.Seller(name, address, taxId));
  }

  /** `CM=1925,GA=1800` : taux de TVA en points de base par pays (à valider avec un expert-comptable avant la production). */
  @Bean
  BillingSettings billingSettings(@Value("${beep.billing.vat-rates}") String rates, @Value("${beep.billing.default-country}") String country,
      @Value("${beep.public-url}") String publicUrl) {
    Map<String, Integer> vat = new LinkedHashMap<>();
    for (String pair : rates.split(",")) {
      String[] kv = pair.trim().split("=");
      if (kv.length == 2) vat.put(kv[0].trim().toUpperCase(Locale.ROOT), Integer.parseInt(kv[1].trim()));
    }
    String defaultCountry = country.toUpperCase(Locale.ROOT);
    if (!vat.containsKey(defaultCountry)) throw new IllegalStateException("BILLING_DEFAULT_COUNTRY sans taux de TVA dans BILLING_VAT_RATES");
    return new BillingSettings(vat, defaultCountry, Duration.ofMinutes(30), publicUrl.replaceAll("/+$", "") + "/app/billing");
  }

  @Bean
  BillingEngine billingEngine(SubscriptionRepository subscriptions, PaymentRepository payments, InvoiceRepository invoices, CouponRepository coupons, PlanRepository plans,
      WorkspaceRepository workspaces, UserRepository users, MailPort mail, InvoicePdfPort pdf, BillingSettings settings) {
    return new BillingEngine(subscriptions, payments, invoices, coupons, plans, workspaces, users, mail, pdf, settings);
  }

  @Bean
  CheckoutUseCase checkout(Workspaces workspaces, WorkspaceRepository workspaceRepository, PlanRepository plans, PaymentRepository payments, PaymentGatewayPort gateway,
      BillingEngine engine, RateLimiterPort limiter, TransactionPort tx, Clock clock) {
    return new CheckoutUseCase(workspaces, workspaceRepository, plans, payments, gateway, engine, limiter, tx, clock);
  }

  @Bean
  SettlePaymentUseCase settlePayment(PaymentRepository payments, PaymentGatewayPort gateway, BillingEngine engine, Workspaces workspaces, TransactionPort tx, Clock clock) {
    return new SettlePaymentUseCase(payments, gateway, engine, workspaces, tx, clock);
  }

  @Bean
  BillingOverviewUseCase billingOverview(Workspaces workspaces, WorkspaceRepository workspaceRepository, PlanRepository plans, SubscriptionRepository subscriptions,
      PaymentRepository payments, InvoiceRepository invoices, InvoicePdfPort pdf, BillingEngine engine, TransactionPort tx, Clock clock) {
    return new BillingOverviewUseCase(workspaces, workspaceRepository, plans, subscriptions, payments, invoices, pdf, engine, tx, clock);
  }

  @Bean
  BillingLifecycleUseCase billingLifecycle(SubscriptionRepository subscriptions, WorkspaceRepository workspaces, PlanRepository plans, UserRepository users,
      PaymentRepository payments, MailPort mail, TransactionPort tx, Clock clock) {
    return new BillingLifecycleUseCase(subscriptions, workspaces, plans, users, payments, mail, tx, clock);
  }

  @Bean
  AdminBillingUseCase adminBilling(SubscriptionRepository subscriptions, PaymentRepository payments, InvoiceRepository invoices, CouponRepository coupons, PlanRepository plans,
      WorkspaceRepository workspaces, InvoicePdfPort pdf, BillingEngine engine, TransactionPort tx, Clock clock) {
    return new AdminBillingUseCase(subscriptions, payments, invoices, coupons, plans, workspaces, pdf, engine, tx, clock);
  }

  // ---- API admin interne : jeton de service (deux jetons acceptés pendant une rotation) ----
  @Bean
  @Profile("!worker")
  FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(@Value("${beep.admin-tokens}") List<String> tokens) {
    FilterRegistrationBean<AdminTokenFilter> reg = new FilterRegistrationBean<>(new AdminTokenFilter(tokens));
    reg.addUrlPatterns("/internal/admin/*");
    return reg;
  }

  @Bean
  io.beep.core.app.port.TrustRepository trustRepository(DSLContext dsl) {return new io.beep.core.adapter.out.postgres.PostgresTrustAdapter(dsl);}
  @Bean
  io.beep.core.app.port.PrivacyRepository privacyRepository(DSLContext dsl) {return new io.beep.core.adapter.out.postgres.PostgresPrivacyAdapter(dsl);}
  @Bean
  io.beep.core.app.trust.TrustUseCase trustUseCase(io.beep.core.app.port.TrustRepository store,TransactionPort tx,RateLimiterPort limiter,Clock clock,PublicSite site) {return new io.beep.core.app.trust.TrustUseCase(store,tx,limiter,clock).withSite(site);}
  @Bean
  io.beep.core.app.privacy.PrivacyUseCase privacyUseCase(io.beep.core.app.port.PrivacyRepository data,io.beep.core.app.port.TrustRepository audit,OneTimeTokenRepository tokens,TokenPort crypto,SecretCipherPort cipher,MailPort mail,RateLimiterPort limiter,TransactionPort tx,Clock clock) {return new io.beep.core.app.privacy.PrivacyUseCase(data,audit,tokens,crypto,cipher,mail,limiter,tx,clock);}
  @Bean
  io.beep.core.app.port.UrlSafetyPort urlSafety(@Value("${URL_SAFETY_PROVIDER:web_risk}") String provider,@Value("${WEB_RISK_API_KEY:}") String webRisk,@Value("${SAFE_BROWSING_API_KEY:}") String safeBrowsing) {return new io.beep.core.adapter.out.safety.GoogleUrlSafetyAdapter(provider,"web_risk".equals(provider)?webRisk:safeBrowsing);}
  @Bean
  io.beep.core.app.port.QuotaLifecycleRepository quotaLifecycleRepository(DSLContext dsl) {return new io.beep.core.adapter.out.postgres.PostgresQuotaLifecycleAdapter(dsl);}
  @Bean
  io.beep.core.app.billing.QuotaLifecycleUseCase quotaLifecycle(io.beep.core.app.port.QuotaLifecycleRepository data,MailPort mail,TransactionPort tx,Clock clock) {return new io.beep.core.app.billing.QuotaLifecycleUseCase(data,mail,tx,clock);}
}
