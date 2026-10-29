package io.beep.core.app;

import io.beep.core.app.admin.AdminEntitlementsUseCase;
import io.beep.core.app.admin.AdminInvitationsUseCase;
import io.beep.core.app.admin.AdminLinksUseCase;
import io.beep.core.app.admin.AdminUsersUseCase;
import io.beep.core.app.analytics.ClickRecorder;
import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.auth.AuthSettings;
import io.beep.core.app.auth.CompleteMfaUseCase;
import io.beep.core.app.auth.Fakes;
import io.beep.core.app.auth.MagicLinkUseCase;
import io.beep.core.app.auth.OAuthUseCase;
import io.beep.core.app.auth.SessionIssuer;
import io.beep.core.app.auth.SignUpUseCase;
import io.beep.core.app.bio.BioPagesUseCase;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.link.DeleteLinkUseCase;
import io.beep.core.app.link.ListLinksUseCase;
import io.beep.core.app.link.UpdateLinkUseCase;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.ClickSinkPort;
import io.beep.core.app.port.GeoIpPort;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.OAuthPort;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.port.QrRendererPort;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.SlugResolverPort;
import io.beep.core.app.port.VisitorSaltRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.qr.QrCodesUseCase;
import io.beep.core.app.redirect.BioBlockClickUseCase;
import io.beep.core.app.redirect.ResolveQrUseCase;
import io.beep.core.app.redirect.ResolveSlugUseCase;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.analytics.ClickEvent;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.qr.QrCode;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Plateforme en mémoire : toutes les doublures des ports et les cas d'usage câblés, pour des tests sans base ni framework. */
final class Platform {
  static final Plan STARTER = new Plan("starter", "Starter", 1, 0, true, Map.of(
      "links.stock_max", 5L, "qr.stock_max", 5L, "bio.max", 1L, "domains.max", 0L, "members.max", 1L, "api.rpm", 0L,
      "analytics.retention_days", 30L, "features.analytics_advanced", 0L, "features.bio_badge_removable", 0L, "features.export_csv", 0L));

  Instant now = Instant.parse("2026-03-10T10:00:00Z");
  final Clock clock = new Clock() {
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  };

  final PublicSite site = new PublicSite("https://beepclick.app/");
  final Fakes.Users users = new Fakes.Users();
  final Fakes.Sessions sessions = new Fakes.Sessions();
  final Fakes.Tokens tokens = new Fakes.Tokens();
  final Fakes.Invitations invitations = new Fakes.Invitations();
  final Fakes.Mail mail = new Fakes.Mail();
  final Fakes.Limiter limiter = new Fakes.Limiter();
  final Fakes.TokenGen tokenGen = new Fakes.TokenGen();
  final Fakes.Cipher cipher = new Fakes.Cipher();
  final Fakes.Hasher hasher = new Fakes.Hasher();
  final Fakes.Tx tx = new Fakes.Tx();
  final Set<String> slugRegistry = new HashSet<>();
  final MemWorkspaces workspaceRepo = new MemWorkspaces();
  final MemPlans plans = new MemPlans();
  final MemLinks links = new MemLinks();
  final MemQr qrCodes = new MemQr();
  final MemBio bioPages = new MemBio();
  final MemSink sink = new MemSink();
  final MemAnalytics analytics = new MemAnalytics();
  final FakeOAuth oauth = new FakeOAuth();
  AuthSettings authSettings = AuthSettings.defaults().withInviteOnly(true);

  final Workspaces workspaces = new Workspaces(workspaceRepo, plans, links, qrCodes, bioPages, clock);
  final VisitorSaltRepository salts = new VisitorSaltRepository() {
    public byte[] saltFor(LocalDate day) { return ("salt-" + day).getBytes(StandardCharsets.UTF_8); }
    public int deleteBefore(LocalDate day) { return 0; }
  };
  final GeoIpPort geoIp = ip -> "203.0.113.9".equals(ip) ? "CM" : null;
  final ClickRecorder recorder = new ClickRecorder(sink, salts, geoIp);
  final SlugResolverPort resolver = new MemResolver();
  final SessionIssuer issuer = new SessionIssuer(sessions, tokens, tokenGen, clock, authSettings);

  final CreateLinkUseCase createLink = new CreateLinkUseCase(workspaces, workspaceRepo, links, tx, site, clock);
  final UpdateLinkUseCase updateLink = new UpdateLinkUseCase(workspaces, links, tx, site, clock);
  final DeleteLinkUseCase deleteLink = new DeleteLinkUseCase(workspaces, links, qrCodes, tx);
  final ListLinksUseCase listLinks = new ListLinksUseCase(workspaces, links, analytics, clock);
  final ResolveSlugUseCase resolveSlug = new ResolveSlugUseCase(resolver, recorder);
  final ResolveQrUseCase resolveQr = new ResolveQrUseCase(resolver, recorder);
  final BioBlockClickUseCase bioClick = new BioBlockClickUseCase(resolver, recorder);
  final GetStatsUseCase stats = new GetStatsUseCase(workspaces, analytics, links, qrCodes, bioPages, clock);
  final QrRendererPort renderer = (content, style, format, size) -> (format + ":" + content).getBytes(StandardCharsets.UTF_8);
  final QrCodesUseCase qr = new QrCodesUseCase(workspaces, workspaceRepo, qrCodes, links, analytics, renderer, tx, site, clock);
  final BioPagesUseCase bio = new BioPagesUseCase(workspaces, workspaceRepo, bioPages, analytics, tx, site, clock);
  final AdminUsersUseCase adminUsers = new AdminUsersUseCase(users, workspaceRepo, workspaces, plans, sessions, tx);
  final AdminLinksUseCase adminLinks = new AdminLinksUseCase(links, tx, clock);
  final AdminEntitlementsUseCase adminEntitlements = new AdminEntitlementsUseCase(plans, workspaceRepo, workspaces, tx, clock);
  final AdminInvitationsUseCase adminInvitations = new AdminInvitationsUseCase(invitations, users, mail, tx, clock);

  SignUpUseCase signUp() {
    return new SignUpUseCase(users, tokens, invitations, hasher, tokenGen, mail, limiter, tx, clock, authSettings);
  }

  MagicLinkUseCase magicLink() {
    return new MagicLinkUseCase(users, tokens, invitations, tokenGen, mail, limiter, tx, issuer, clock, authSettings);
  }

  CompleteMfaUseCase mfa() {
    return new CompleteMfaUseCase(users, tokens, tokenGen, cipher, limiter, issuer, clock, authSettings);
  }

  OAuthUseCase oauthUseCase() {
    return new OAuthUseCase(oauth, users, invitations, tokenGen, tx, issuer, site, clock, authSettings);
  }

  User verifiedUser(String email) {
    User u = User.register(UUID.randomUUID(), new Email(email), hasher.hash("correct-horse-battery"), "fr", now).verifyEmail(now);
    users.insert(u);
    return u;
  }

  // ---------------------------------------------------------------------------------------------------------------

  class MemWorkspaces implements WorkspaceRepository {
    final Map<UUID, Workspace> byId = new LinkedHashMap<>();
    int locks;

    public Optional<Workspace> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<Workspace> findByOwner(UUID owner) { return byId.values().stream().filter(w -> w.ownerId().equals(owner)).findFirst(); }
    public Workspace ensure(Workspace c) { return findByOwner(c.ownerId()).orElseGet(() -> { byId.put(c.id(), c); return c; }); }
    public void lock(UUID id) { locks++; }
    public void updateStatus(UUID id, WorkspaceStatus s) { byId.computeIfPresent(id, (k, w) -> w.withStatus(s)); }
    public void updatePlan(UUID id, String plan) {
      byId.computeIfPresent(id, (k, w) -> new Workspace(w.id(), w.ownerId(), w.name(), plan, w.status(), w.firstLinkAt(), w.createdAt()));
    }
    public void markFirstLink(UUID id, Instant at) {
      byId.computeIfPresent(id, (k, w) -> w.firstLinkAt() != null ? w : new Workspace(w.id(), w.ownerId(), w.name(), w.planCode(), w.status(), at, w.createdAt()));
    }
  }

  class MemPlans implements PlanRepository {
    final Map<UUID, Map<String, EntitlementOverride>> overrides = new HashMap<>();
    final Map<String, Plan> published = new LinkedHashMap<>(Map.of("starter", STARTER));

    public Optional<Plan> findByCode(String code) { return Optional.ofNullable(published.get(code)); }
    public List<Plan> findAll() { return List.copyOf(published.values()); }
    public void publish(Plan plan, String staffId, Instant at) { published.put(plan.code(), plan); }
    public List<EntitlementOverride> overrides(UUID ws) { return List.copyOf(overrides.getOrDefault(ws, Map.of()).values()); }
    public void applyOverrides(UUID ws, Map<String, Long> changes, String reason, String staffId, Instant at) {
      Map<String, EntitlementOverride> m = overrides.computeIfAbsent(ws, k -> new LinkedHashMap<>());
      changes.forEach((k, v) -> { if (v == null) m.remove(k); else m.put(k, new EntitlementOverride(k, v, reason, at, staffId)); });
    }
  }

  class MemLinks implements LinkRepository {
    final Map<UUID, Link> byId = new LinkedHashMap<>();

    public void insert(Link l) {
      if (!slugRegistry.add(l.registryKey())) throw new SlugRegistryPort.SlugTakenException();
      byId.put(l.id(), l);
    }
    public void update(Link l) {
      Link old = byId.get(l.id());
      if (!old.slug().equals(l.slug())) {
        if (!slugRegistry.add(l.registryKey())) throw new SlugRegistryPort.SlugTakenException();
        slugRegistry.remove(old.registryKey());
      }
      byId.put(l.id(), l);
    }
    public void delete(UUID id) { Link l = byId.remove(id); if (l != null) slugRegistry.remove(l.registryKey()); }
    public Optional<Link> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Page page(UUID ws, String q, int limit, String cursor) {
      return new Page(byId.values().stream().filter(l -> l.workspaceId().equals(ws)).filter(l -> q == null || l.slug().value().contains(q) || l.destination().url().contains(q))
          .sorted(Comparator.comparing(Link::createdAt).reversed()).limit(limit).toList(), null);
    }
    public long count(UUID ws) { return byId.values().stream().filter(l -> l.workspaceId().equals(ws)).count(); }
    public long countCreatedSince(UUID ws, Instant since) { return byId.values().stream().filter(l -> l.workspaceId().equals(ws) && !l.createdAt().isBefore(since)).count(); }
    public AdminPage adminSearch(String q, UUID ws, int limit, String cursor) {
      return new AdminPage(byId.values().stream().filter(l -> q == null || l.slug().value().contains(q)).map(l -> new AdminRow(l, owner(l))).toList(), null);
    }
    public Optional<AdminRow> adminFind(UUID id) { return findById(id).map(l -> new AdminRow(l, owner(l))); }
    private String owner(Link l) {
      return workspaceRepo.findById(l.workspaceId()).flatMap(w -> users.findById(w.ownerId())).map(u -> u.email().value()).orElse(null);
    }
  }

  class MemQr implements QrCodeRepository {
    final Map<UUID, QrCode> byId = new LinkedHashMap<>();

    public void insert(QrCode q) { byId.put(q.id(), q); }
    public void update(QrCode q) { byId.put(q.id(), q); }
    public void delete(UUID id) { byId.remove(id); }
    public Optional<QrCode> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Page page(UUID ws, int limit, String cursor) { return new Page(byId.values().stream().filter(q -> q.workspaceId().equals(ws)).toList(), null); }
    public long countDynamic(UUID ws) { return byId.values().stream().filter(q -> q.workspaceId().equals(ws) && q.dynamic()).count(); }
    public long countDynamicCreatedSince(UUID ws, Instant since) { return countDynamic(ws); }
    public long countByLink(UUID linkId) { return byId.values().stream().filter(q -> linkId.equals(q.linkId())).count(); }
  }

  class MemBio implements BioPageRepository {
    final Map<UUID, BioPage> byId = new LinkedHashMap<>();

    public void insert(BioPage p) {
      if (!slugRegistry.add(p.slug().value())) throw new SlugRegistryPort.SlugTakenException();
      byId.put(p.id(), p);
    }
    public void update(BioPage p) {
      BioPage old = byId.get(p.id());
      if (!old.slug().equals(p.slug())) {
        if (!slugRegistry.add(p.slug().value())) throw new SlugRegistryPort.SlugTakenException();
        slugRegistry.remove(old.slug().value());
      }
      byId.put(p.id(), p.withBlocks(old.blocks(), p.updatedAt()));
    }
    public void replaceBlocks(UUID id, List<BioBlock> blocks, Instant at) { byId.computeIfPresent(id, (k, p) -> p.withBlocks(blocks, at)); }
    public void delete(UUID id) { BioPage p = byId.remove(id); if (p != null) slugRegistry.remove(p.slug().value()); }
    public Optional<BioPage> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public List<BioPage> listByWorkspace(UUID ws) { return byId.values().stream().filter(p -> p.workspaceId().equals(ws)).toList(); }
    public long count(UUID ws) { return listByWorkspace(ws).size(); }
  }

  class MemResolver implements SlugResolverPort {
    public Resolution resolve(String slug) {
      for (Link l : links.byId.values()) {
        if (l.registryKey().equals(slug)) return new Resolution(Resolution.Kind.LINK, l.id(), l.workspaceId(), l.destination().url(), l.disabled(), suspended(l.workspaceId()), l.rules(), l.passwordHash());
      }
      for (BioPage p : bioPages.byId.values()) {
        if (p.slug().value().equals(slug)) return new Resolution(Resolution.Kind.BIO, p.id(), p.workspaceId(), null, false, suspended(p.workspaceId()));
      }
      return Resolution.NONE;
    }
    public Optional<QrTarget> resolveQr(String code) {
      return qrCodes.byId.values().stream().filter(q -> code.equals(q.code())).findFirst().map(q -> {
        Link l = links.byId.get(q.linkId());
        return new QrTarget(q.id(), q.workspaceId(), l.id(), l.destination().url(), l.disabled(), suspended(q.workspaceId()), l.rules(), l.passwordHash());
      });
    }
    public Optional<PublishedBio> bioPage(UUID id) { return bioPages.findById(id).map(p -> new PublishedBio(p, suspended(p.workspaceId()))); }
    private boolean suspended(UUID ws) { return workspaceRepo.findById(ws).map(Workspace::suspended).orElse(false); }
  }

  static class MemSink implements ClickSinkPort {
    final List<ClickEvent> events = new ArrayList<>();

    public boolean offer(ClickEvent e) { events.add(e); return true; }
    public Stats stats() { return new Stats(0, 0, events.size()); }
  }

  static class MemAnalytics implements AnalyticsRepository {
    final Map<UUID, Long> totals = new HashMap<>();
    final List<String> breakdownsAsked = new ArrayList<>();

    public Totals totals(Resource r, UUID id, LocalDate from, LocalDate to) { return new Totals(totals.getOrDefault(id, 0L), 0); }
    public List<Point> daily(Resource r, UUID id, LocalDate from, LocalDate to) {
      List<Point> out = new ArrayList<>();
      for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.add(new Point(d.toString(), 0, 0));
      return out;
    }
    public List<Point> hourly(Resource r, UUID id, Instant from, Instant to) {
      List<Point> out = new ArrayList<>();
      for (Instant h = from; h.isBefore(to); h = h.plusSeconds(3600)) out.add(new Point(h.toString(), 0, 0));
      return out;
    }
    public List<Breakdown> breakdown(Resource r, UUID id, String dim, LocalDate from, LocalDate to, int top) { breakdownsAsked.add(dim); return List.of(); }
    public Map<UUID, Long> totalsByResource(Resource r, Collection<UUID> ids, LocalDate from, LocalDate to) {
      Map<UUID, Long> m = new HashMap<>();
      for (UUID id : ids) if (totals.containsKey(id)) m.put(id, totals.get(id));
      return m;
    }
    public List<Point> workspaceDaily(UUID ws, LocalDate from, LocalDate to) { return daily(Resource.LINK, ws, from, to); }
  }

  static class FakeOAuth implements OAuthPort {
    Identity next = new Identity("ada@example.com", true, "sub-1");
    String lastVerifier;

    public boolean enabled(Provider p) { return p == Provider.GOOGLE; }
    public String authorizationUrl(Provider p, String state, String challenge, String redirectUri) { return "https://accounts.example/auth?state=" + state + "&code_challenge=" + challenge; }
    public Identity exchange(Provider p, String code, String verifier, String redirectUri) {
      if (code.equals("bad")) throw new OAuthFailedException("refusé", null);
      lastVerifier = verifier;
      return next;
    }
  }

}
