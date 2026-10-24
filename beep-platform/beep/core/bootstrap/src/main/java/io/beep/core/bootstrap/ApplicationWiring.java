package io.beep.core.bootstrap;

import io.beep.core.adapter.in.admin.AdminTokenFilter;
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
  CacheInvalidationListener cacheInvalidationListener(CachingSlugResolverAdapter cache, @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String user, @Value("${spring.datasource.password}") String password) {
    return new CacheInvalidationListener(url, user, password, cache::invalidate, cache::invalidateAll).start();
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
  CreateLinkUseCase createLink(Workspaces workspaces, WorkspaceRepository workspaceRepository, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock) {
    return new CreateLinkUseCase(workspaces, workspaceRepository, links, tx, site, clock);
  }

  @Bean
  UpdateLinkUseCase updateLink(Workspaces workspaces, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock) {
    return new UpdateLinkUseCase(workspaces, links, tx, site, clock);
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
  ResolveSlugUseCase resolveSlug(SlugResolverPort resolver, ClickRecorder recorder) {
    return new ResolveSlugUseCase(resolver, recorder);
  }

  @Bean
  ResolveQrUseCase resolveQr(SlugResolverPort resolver, ClickRecorder recorder) {
    return new ResolveQrUseCase(resolver, recorder);
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
  AdminLinksUseCase adminLinks(LinkRepository links, TransactionPort tx, Clock clock) {
    return new AdminLinksUseCase(links, tx, clock);
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

  // ---- API admin interne : jeton de service (deux jetons acceptés pendant une rotation) ----
  @Bean
  @Profile("!worker")
  FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(@Value("${beep.admin-tokens}") List<String> tokens) {
    FilterRegistrationBean<AdminTokenFilter> reg = new FilterRegistrationBean<>(new AdminTokenFilter(tokens));
    reg.addUrlPatterns("/internal/admin/*");
    return reg;
  }
}
