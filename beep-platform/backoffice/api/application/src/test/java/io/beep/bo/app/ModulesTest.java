package io.beep.bo.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.bo.app.audit.AuditLogUseCase;
import io.beep.bo.app.auth.StaffSettings;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.billing.BillingUseCase;
import io.beep.bo.app.catalog.CatalogUseCase;
import io.beep.bo.app.domains.DomainsUseCase;
import io.beep.bo.app.plans.PlanDraftsUseCase;
import io.beep.bo.app.port.PlanDraftRepository;
import java.util.Optional;
import io.beep.bo.app.links.LinksModerationUseCase;
import io.beep.bo.app.plans.EntitlementsUseCase;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.users.UserAdminUseCase;
import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Modules de la phase 1 : chaque action exige le bon droit, les décisions sensibles une ré-authentification, et tout est journalisé. */
class ModulesTest {
  static final String SECRET = Totp.newSecret();
  final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
  final List<AuditEvent> audit = new ArrayList<>();
  final AuditPort auditPort = new AuditPort() {
    public void append(AuditEvent e) { audit.add(e); }
    public List<AuditChain.Entry> readAll() { return List.of(); }
    public Page page(int limit, Long before, String action, String actor) { return new Page(List.of(), null); }
  };
  final Map<String, Integer> hits = new HashMap<>();
  final RateLimiterPort limiter = new RateLimiterPort() {
    public boolean tryAcquire(String k, int max, Duration w) { return hits.merge(k, 1, Integer::sum) <= max; }
    public void reset(String k) { hits.remove(k); }
  };
  final SecretCipherPort cipher = new SecretCipherPort() {
    public String encrypt(String p) { return "enc:" + p; }
    public String decrypt(String c) { return c.substring(4); }
  };
  final FakeCore core = new FakeCore();
  final StepUp stepUp = new StepUp(cipher, limiter, clock, StaffSettings.defaults());

  StaffUser staff(Role role) {
    return new StaffUser(UUID.randomUUID(), new Email(role.db() + "@beep.co"), "h", role, "enc:" + SECRET, true, clock.instant());
  }

  String code() {
    return Totp.code(SECRET, clock.instant());
  }

  @Test
  void accountPrivacyAndFlagsRequireTheirOwnPermissionAndFreshTotp() {
    var gateway=new FakeCore() {
      @Override public Map<String,Object> exportUser(UUID id,UUID staff){calls.add("privacy.export");return Map.of("user",id);}
      @Override public void eraseUser(UUID id,String reason,UUID staff){calls.add("privacy.erase");}
      @Override public FeatureFlags featureFlags(UUID staff){calls.add("flags.read");return new FeatureFlags(List.of());}
    };
    var trust=new io.beep.bo.app.trust.TrustConsoleUseCase(gateway,auditPort,stepUp,clock);
    assertThatThrownBy(()->trust.export(staff(Role.SUPPORT),FakeCore.USER,code(),"ip")).hasMessage("forbidden");
    assertThatThrownBy(()->trust.erase(staff(Role.READONLY),FakeCore.USER,"verified request",code(),"ip")).hasMessage("forbidden");
    assertThatThrownBy(()->trust.flags(staff(Role.TRUST_SAFETY))).hasMessage("forbidden");
    var actor=staff(Role.TRUST_SAFETY);
    assertThatThrownBy(()->trust.export(actor,FakeCore.USER,"bad-code","ip")).hasMessage("reauth_required");
    assertThatThrownBy(()->trust.erase(actor,FakeCore.USER,null,code(),"ip")).hasMessage("invalid_reason");
    assertThat(gateway.calls).isEmpty();
    trust.export(actor,FakeCore.USER,code(),"ip");trust.erase(actor,FakeCore.USER,"verified request",code(),"ip");trust.flags(staff(Role.SUPERADMIN));
    assertThat(gateway.calls).containsExactly("privacy.export","privacy.erase","flags.read");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("privacy.export","privacy.erase");
  }

  @Test
  void suspendingAnAccountNeedsTheRightPermissionAReasonAndAFreshTotp() {
    var users = new UserAdminUseCase(core, auditPort, stepUp, clock);
    StaffUser support = staff(Role.SUPPORT);
    assertThatThrownBy(() -> users.setStatus(support, FakeCore.USER, "suspended", "spam", code(), "ip")).hasMessage("forbidden");

    StaffUser ts = staff(Role.TRUST_SAFETY);
    assertThatThrownBy(() -> users.setStatus(ts, FakeCore.USER, "suspended", "", code(), "ip")).hasMessage("invalid_reason");
    assertThatThrownBy(() -> users.setStatus(ts, FakeCore.USER, "suspended", "spam massif", "000000", "ip")).hasMessage("reauth_required");
    assertThat(core.calls).isEmpty(); // rien n'est parti vers le core

    users.setStatus(ts, FakeCore.USER, "suspended", "spam massif", code(), "10.0.0.1");
    assertThat(core.calls).containsExactly("status:suspended:" + ts.id());
    assertThat(audit).singleElement().satisfies(e -> {
      assertThat(e.action()).isEqualTo("users.suspend");
      assertThat(e.target()).isEqualTo("user:" + FakeCore.USER);
      assertThat(e.detail()).contains("motif=spam massif");
      assertThat(e.ip()).isEqualTo("10.0.0.1");
    });
  }

  @Test
  void stepUpIsThrottled() {
    StaffUser ts = staff(Role.TRUST_SAFETY);
    for (int i = 0; i < 5; i++) assertThatThrownBy(() -> stepUp.require(ts, "000000")).hasMessage("reauth_required");
    assertThatThrownBy(() -> stepUp.require(ts, code())).hasMessage("rate_limited");
  }

  @Test
  void viewingAUserIsAudited() {
    new UserAdminUseCase(core, auditPort, stepUp, clock).detail(staff(Role.READONLY), FakeCore.USER, "ip");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("users.view");
  }

  @Test
  void linkModerationIsReservedToTrustAndSafetyAndRecordsTheReason() {
    var links = new LinksModerationUseCase(core, auditPort, clock);
    assertThatThrownBy(() -> links.disable(staff(Role.SUPPORT), FakeCore.LINK, "hameçonnage", "ip")).hasMessage("forbidden");
    StaffUser ts = staff(Role.TRUST_SAFETY);
    assertThat(links.disable(ts, FakeCore.LINK, " hameçonnage signalé ", "ip").status()).isEqualTo("disabled");
    links.enable(ts, FakeCore.LINK, "ip");
    links.search(staff(Role.READONLY), "promo", null, 20, null, "ip");
    assertThat(core.calls).containsExactly("disable:hameçonnage signalé", "enable", "links:promo");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("links.disable", "links.enable", "links.search");
    assertThat(audit.get(0).detail()).contains("slug=promo").contains("motif=hameçonnage signalé");
  }

  @Test
  void overridesNeedReauthenticationAndAreAuditedWithTheirValues() {
    var entitlements = new EntitlementsUseCase(core, auditPort, stepUp, clock);
    UUID ws = UUID.randomUUID();
    assertThatThrownBy(() -> entitlements.setOverrides(staff(Role.FINANCE), ws, Map.of("links.stock_max", 55L), "geste", code(), "ip")).hasMessage("forbidden");
    StaffUser support = staff(Role.SUPPORT);
    Map<String, Long> changes = new HashMap<>();
    changes.put("links.stock_max", 55L);
    changes.put("qr.stock_max", null);
    entitlements.setOverrides(support, ws, changes, "geste commercial", code(), "ip");
    assertThat(audit).singleElement().satisfies(e -> {
      assertThat(e.action()).isEqualTo("entitlements.override");
      assertThat(e.detail()).contains("links.stock_max=55").contains("qr.stock_max=plan").contains("motif=geste commercial");
    });
  }

  @Test
  void auditLogIsVisibleToSuperadminAndTrustSafetyOnlyAndReadingItIsAudited() {
    var log = new AuditLogUseCase(auditPort, clock);
    assertThatThrownBy(() -> log.execute(staff(Role.SUPPORT), 50, null, null, null, "ip")).hasMessage("forbidden");
    assertThat(log.execute(staff(Role.SUPERADMIN), 50, null, "links.", null, "ip").intact()).isTrue();
    assertThat(audit).extracting(AuditEvent::action).containsExactly("audit.read");
  }

  // ---- Phase 2 : facturation ----
  @Test
  void billingReadsNeedTheBillingPermissionAndWritesNeedFreshTotpAndAReference() {
    var billing = new BillingUseCase(core, auditPort, stepUp, clock);
    UUID ws = UUID.randomUUID();
    assertThatThrownBy(() -> billing.summary(staff(Role.TRUST_SAFETY))).hasMessage("forbidden");
    assertThat(billing.summary(staff(Role.SUPPORT)).mrrXaf()).isEqualTo(4999);
    assertThatThrownBy(() -> billing.recordManualPayment(staff(Role.SUPPORT), ws, "pro", "monthly", 4999, "Virement UBA", null, code(), "ip")).hasMessage("forbidden");

    StaffUser finance = staff(Role.FINANCE);
    assertThatThrownBy(() -> billing.recordManualPayment(finance, ws, "pro", "monthly", 4999, "", null, code(), "ip")).hasMessage("invalid_reason");
    assertThatThrownBy(() -> billing.recordManualPayment(finance, ws, "pro", "monthly", 4999, "Virement UBA", null, "000000", "ip")).hasMessage("reauth_required");
    assertThat(core.calls).isEmpty();

    billing.recordManualPayment(finance, ws, "pro", "monthly", 4999, " Virement UBA 123 ", "CM", code(), "10.0.0.2");
    assertThat(core.calls).containsExactly("manual:pro:4999:Virement UBA 123");
    assertThat(audit).singleElement().satisfies(e -> {
      assertThat(e.action()).isEqualTo("billing.manual_payment");
      assertThat(e.target()).isEqualTo("workspace:" + ws);
      assertThat(e.detail()).contains("montant=4999").contains("facture=BEEP-2026-000001").contains("référence=Virement UBA 123");
    });
  }

  @Test
  void planChangesCouponsAndInvoiceDownloadsAreAudited() {
    var billing = new BillingUseCase(core, auditPort, stepUp, clock);
    StaffUser finance = staff(Role.FINANCE);
    UUID ws = UUID.randomUUID();
    assertThatThrownBy(() -> billing.changePlan(finance, ws, "elite", clock.instant().plusSeconds(86400), "", code(), "ip")).hasMessage("invalid_reason");
    billing.changePlan(finance, ws, "elite", clock.instant().plusSeconds(86400), "Partenaire", code(), "ip");
    billing.createCoupon(finance, "WELCOME20", 20, null, 100, null, List.of("pro"), code(), "ip");
    billing.setCouponActive(finance, "WELCOME20", false, code(), "ip");
    billing.invoicePdf(staff(Role.SUPPORT), "BEEP-2026-000001", "ip");
    assertThat(audit).extracting(AuditEvent::action)
        .containsExactly("billing.plan_change", "billing.coupon_create", "billing.coupon_disable", "billing.invoice_download");
    assertThatThrownBy(() -> billing.createCoupon(staff(Role.SUPPORT), "X1234", 10, null, null, null, List.of(), code(), "ip")).hasMessage("forbidden");
  }

  @Test
  void planDraftsAreEditedByFinanceButPublishedOnlyBySuperadminWithTotp() {
    Map<String, PlanDraftRepository.Draft> store = new HashMap<>();
    PlanDraftRepository repo = new PlanDraftRepository() {
      public List<Draft> list() { return List.copyOf(store.values()); }
      public Optional<Draft> find(String code) { return Optional.ofNullable(store.get(code)); }
      public void save(Draft d) { store.put(d.code(), d); }
      public void delete(String code) { store.remove(code); }
      public void markPublished(String code, int version, Instant at) {
        Draft d = store.get(code);
        store.put(code, new Draft(d.code(), d.name(), d.priceXaf(), d.priceQuarterlyXaf(), d.priceAnnualXaf(), d.isPublic(), d.entitlements(), version, d.note(), d.updatedBy(),
            d.updatedAt(), version, at));
      }
    };
    var drafts = new PlanDraftsUseCase(repo, core, auditPort, stepUp, clock);
    StaffUser finance = staff(Role.FINANCE);
    StaffUser admin = staff(Role.SUPERADMIN);

    assertThatThrownBy(() -> drafts.save(staff(Role.SUPPORT), "pro", "Pro", 5499, null, null, true, Map.of("links.stock_max", 60L), 1, null, "ip")).hasMessage("forbidden");
    assertThatThrownBy(() -> drafts.save(finance, "Pro!", "Pro", 5499, null, null, true, Map.of(), 1, null, "ip")).hasMessage("invalid_request");
    drafts.save(finance, "pro", "Pro", 5499, null, null, true, Map.of("links.stock_max", 60L), 1, "hausse de prix", "ip");
    assertThat(core.calls).isEmpty(); // un brouillon n'atteint jamais le core

    assertThatThrownBy(() -> drafts.publish(finance, "pro", code(), "ip")).hasMessage("forbidden");
    assertThatThrownBy(() -> drafts.publish(admin, "pro", "000000", "ip")).hasMessage("reauth_required");
    var published = drafts.publish(admin, "pro", code(), "ip");
    assertThat(published.version()).isEqualTo(2);
    assertThat(core.calls).containsExactly("publish:pro:5499");
    assertThat(store.get("pro").publishedVersion()).isEqualTo(2);
    assertThat(audit.get(audit.size() - 1).detail()).contains("version=1->2").contains("prix=4999->5499");

    // le brouillon est resté sur la version 1 de départ : la publication est refusée tant qu'il n'est pas repris sur la version en ligne
    drafts.save(finance, "pro", "Pro", 5999, null, null, true, Map.of(), 1, null, "ip");
    assertThatThrownBy(() -> drafts.publish(admin, "pro", code(), "ip")).hasMessage("draft_outdated");
    drafts.save(finance, "pro", "Pro", 5999, null, null, true, Map.of(), 2, null, "ip");
    assertThat(drafts.publish(admin, "pro", code(), "ip").version()).isEqualTo(3);
  }

  @Test
  void domainBlockingNeedsPermissionReasonAndTotpAndIsAudited() {
    var uc = new DomainsUseCase(core, auditPort, stepUp, clock);
    UUID id = UUID.randomUUID();
    assertThat(uc.list(staff(Role.SUPPORT), null, null, 20, null).items()).hasSize(1);
    assertThatThrownBy(() -> uc.block(staff(Role.SUPPORT), id, "hameçonnage", code(), "ip")).hasMessage("forbidden");
    assertThatThrownBy(() -> uc.list(staff(Role.FINANCE), null, null, 20, null)).hasMessage("forbidden");

    StaffUser ts = staff(Role.TRUST_SAFETY);
    assertThatThrownBy(() -> uc.block(ts, id, "", code(), "ip")).hasMessage("invalid_reason");
    assertThatThrownBy(() -> uc.block(ts, id, "hameçonnage", "000000", "ip")).hasMessage("reauth_required");
    assertThat(core.calls).isEmpty();
    uc.block(ts, id, " hameçonnage signalé ", code(), "10.0.0.3");
    uc.recheck(ts, id, "ip");
    uc.unblock(ts, id, code(), "ip");
    assertThat(core.calls).containsExactly("blockDomain:hameçonnage signalé", "recheckDomain", "unblockDomain");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("domains.block", "domains.recheck", "domains.unblock");
    assertThat(audit.get(0).detail()).contains("liens=3").contains("motif=hameçonnage signalé");
  }

  @Test
  void catalogPublicationNeedsTheRightTotpAndIsAudited() {
    var uc = new CatalogUseCase(core, auditPort, stepUp, clock);
    assertThat(uc.list(staff(Role.SUPPORT))).hasSize(1);
    assertThat(uc.test(staff(Role.SUPPORT), "https://youtube.com/x", "iphone-safari", null).appFound()).isTrue();
    assertThatThrownBy(() -> uc.publish(staff(Role.SUPPORT), "demo", "Demo", List.of("demo.example"), "demo://x", null, null, true, code(), "ip")).hasMessage("forbidden");
    StaffUser content = staff(Role.CONTENT);
    assertThatThrownBy(() -> uc.publish(content, "demo", "Demo", List.of("demo.example"), "demo://x", null, null, true, "000000", "ip")).hasMessage("reauth_required");
    assertThat(core.calls).isEmpty();
    uc.publish(content, "demo", "Demo", List.of("demo.example"), "demo://x", null, null, true, code(), "ip");
    uc.setEnabled(content, "demo", false, code(), "ip");
    assertThat(core.calls).containsExactly("publishApp:demo", "appEnabled:demo:false");
    assertThat(audit).extracting(AuditEvent::action).containsExactly("catalog.publish", "catalog.disable");
  }
}
