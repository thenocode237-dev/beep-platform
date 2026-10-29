package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.port.TrustRepository;
import io.beep.core.domain.link.*;
import io.beep.core.domain.user.*;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.billing.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class TrustPrivacyIT {
  static final Instant NOW=Instant.parse("2026-10-25T10:00:00Z");
  static DSLContext dsl;
  User user;Workspace ws;PostgresLinkAdapter links;PostgresTrustAdapter trust;
  @BeforeAll static void start() {
    var pg=new PGSimpleDataSource();String url=System.getenv("TEST_DATABASE_URL");
    if(url==null||url.isBlank()){var c=new PostgreSQLContainer<>("postgres:16-alpine");c.start();url=c.getJdbcUrl();pg.setUser(c.getUsername());pg.setPassword(c.getPassword());}
    pg.setUrl(url);var f=Flyway.configure().dataSource(pg).cleanDisabled(false).load();f.clean();f.migrate();f.validate();dsl=DSL.using(pg,SQLDialect.POSTGRES);
  }
  @BeforeEach void init() {
    dsl.execute("TRUNCATE users,slugs,abuse_reports,blocked_hosts,accounting_archive,erased_workspaces CASCADE");
    dsl.execute("UPDATE feature_flags SET enabled=true,rollout_percent=100,version=1");
    user=User.register(UUID.randomUUID(),new Email("privacy@example.com"),"PASSWORD_SECRET","fr",NOW).verifyEmail(NOW);
    new PostgresUserAdapter(dsl).insert(user);
    ws=new PostgresWorkspaceAdapter(dsl).ensure(Workspace.personal(UUID.randomUUID(),user.id(),"Privacy",NOW));
    links=new PostgresLinkAdapter(dsl);trust=new PostgresTrustAdapter(dsl);
  }
  Link link(String slug,String url) {var l=Link.create(UUID.randomUUID(),ws.id(),new Slug(slug),Destination.parse(url),null,NOW);links.insert(l);return l;}
  long count(String table){return dsl.fetchOne("SELECT count(*) FROM "+table).get(0,Long.class);}
  void lifecycle(Instant now){dsl.transaction(c -> new PostgresQuotaLifecycleAdapter(c.dsl()).apply(now));}
  void expired(){new PostgresSubscriptionAdapter(dsl).save(Subscription.none(ws.id(),NOW).extend("pro",BillingCycle.MONTHLY,4999,NOW.minus(Duration.ofDays(31)) ).withStatus(SubscriptionStatus.EXPIRED,NOW));}

  @Test void reportsHaveAnOverdueQueueAndCannotBeResolvedTwice() {
    UUID id=UUID.randomUUID();trust.report(new TrustRepository.Report(id,"https://unsafe.example","phishing","test","public","open",null,NOW.minus(Duration.ofHours(25)),null));
    assertThat(trust.reports("open",50,0,NOW).overdue()).isEqualTo(1);
    trust.resolve(id,"dismissed","false alarm","staff",NOW);
    assertThat(trust.reports("open",50,0,NOW).open()).isZero();
    assertThatThrownBy(()->trust.resolve(id,"blocked","again","staff",NOW)).hasMessageContaining("conflict");
  }
  @Test void hostBlockingIncludesAlternateDestinationsAndRespectsHostBoundaries() {
    Link bad=link("bad","https://safe.example");
    links.update(bad.withRules(LinkRules.of(null,NOW,true,new LinkRules.Onelink("https://m.unsafe.example/path",null,null),List.of(),List.of(),null),null,NOW));
    Link safe=link("safe","https://notunsafe.example");
    trust.block("unsafe.example",true,"phishing","staff",NOW);
    assertThat(trust.blocked("x.unsafe.example")).isTrue();assertThat(trust.blocked("notunsafe.example")).isFalse();
    assertThat(links.findById(bad.id()).orElseThrow().status().db()).isEqualTo("disabled");
    assertThat(links.findById(safe.id()).orElseThrow().status().db()).isEqualTo("active");
    trust.block("unsafe.example",false,"reviewed","staff",NOW);
    assertThat(links.findById(bad.id()).orElseThrow().status().db()).isEqualTo("active");
  }
  @Test void flagsRejectStaleVersionsAndAuditIsAppendOnly() {
    var flag=trust.publishFlag("links.rules",false,20,1,"disable","staff",NOW);assertThat(flag.version()).isEqualTo(2);
    assertThatThrownBy(()->trust.publishFlag("links.rules",true,100,1,"stale","staff",NOW)).hasMessageContaining("conflict");
    trust.audit("staff","test","x","test");assertThatThrownBy(()->dsl.execute("DELETE FROM trust_audit")).hasMessageContaining("append-only");
  }
  @Test void shortLinkReportsResolveToTheDestinationAndScansGuardConcurrentEdits() {
    var l=link("reported","https://unsafe.example/path");
    assertThat(trust.destinationOf("https://beepclick.app/reported","beepclick.app")).contains("https://unsafe.example/path");
    assertThat(trust.destinationOf("https://attacker.example/reported","beepclick.app")).isEmpty();
    var target=trust.scanTargets(50).getFirst();
    links.update(l.edit(l.slug(),Destination.parse("https://changed.example"),null,NOW.plusSeconds(1)));
    assertThat(trust.scanned(target.id(),target.url(),target.updatedAt(),NOW.plusSeconds(2))).isFalse();
  }
  @Test void exportExcludesSecretsAndErasureRevokesAccessAndCleansRegistry() {
    var l=link("erase","https://target.example");links.update(l.withRules(LinkRules.NONE,"LINK_SECRET",NOW));
    var qr=io.beep.core.domain.qr.QrCode.dynamic(UUID.randomUUID(),ws.id(),"QrErase1",l.id(),io.beep.core.domain.qr.QrStyle.DEFAULT,null,NOW);
    new PostgresQrCodeAdapter(dsl).insert(qr);
    UUID domain=UUID.randomUUID();
    dsl.execute("INSERT INTO custom_domains(id,workspace_id,hostname,verify_token,created_at) VALUES (?,?,?,'DNS_SECRET',?::timestamptz)",domain,ws.id(),"privacy.example",NOW.atOffset(ZoneOffset.UTC));
    dsl.execute("UPDATE links SET domain_id=? WHERE id=?",domain,l.id());
    new PostgresBioPageAdapter(dsl).insert(io.beep.core.domain.bio.BioPage.create(UUID.randomUUID(),ws.id(),new Slug("privacybio"),"Private bio",NOW));
    dsl.execute("INSERT INTO sessions(id,user_id,token_hash,created_at,expires_at) VALUES (?,?,?,?::timestamptz,?::timestamptz)",UUID.randomUUID(),user.id(),"SESSION_SECRET",NOW.atOffset(ZoneOffset.UTC),NOW.plusSeconds(3600).atOffset(ZoneOffset.UTC));
    var data=new PostgresPrivacyAdapter(dsl);String exported=data.export(user.id()).toString();
    assertThat(exported).contains(user.email().value(),"https://target.example").doesNotContain("PASSWORD_SECRET","SESSION_SECRET","LINK_SECRET","DNS_SECRET");
    dsl.transaction(c -> new PostgresPrivacyAdapter(c.dsl()).erase(user.id()));
    assertThat(count("users")).isZero();assertThat(count("sessions")).isZero();assertThat(count("links")).isZero();assertThat(count("slugs")).isZero();
    assertThat(count("erased_workspaces")).isEqualTo(1);
    assertThat(count("qr_codes")).isZero();assertThat(count("custom_domains")).isZero();assertThat(count("bio_pages")).isZero();
  }
  @Test void accountingSurvivesErasureInASeparateArchiveAndAdvancedQrStylesRoundTrip() {
    var l=link("branded","https://target.example");
    var style=io.beep.core.domain.qr.QrStyle.DEFAULT.decorated(new io.beep.core.domain.qr.QrStyle.Decoration("rounded","#304F36",null,"scan-fr"));
    var qr=io.beep.core.domain.qr.QrCode.dynamic(UUID.randomUUID(),ws.id(),"BrandQr1",l.id(),style,null,NOW);
    var qrRepo=new PostgresQrCodeAdapter(dsl);qrRepo.insert(qr);assertThat(qrRepo.findById(qr.id()).orElseThrow().style()).isEqualTo(style);
    var payment=new Payment(UUID.randomUUID(),ws.id(),"pro",BillingCycle.MONTHLY,PaymentMethod.MTN_MOMO,"sandbox","archive-1",PaymentStatus.SUCCEEDED,4999,0,0,4999,null,"CM","0001",null,null,null,NOW,NOW);
    new PostgresPaymentAdapter(dsl).insert(payment);
    var invoice=new Invoice("",payment.id(),ws.id(),NOW,"Privacy",user.email().value(),"CM","Pro",BillingCycle.MONTHLY,NOW,NOW.plus(Duration.ofDays(30)),4999,0,0,4999,4192,807,1925,PaymentMethod.MTN_MOMO);
    new PostgresInvoiceAdapter(dsl).issue(invoice);
    dsl.transaction(c -> new PostgresPrivacyAdapter(c.dsl()).erase(user.id()));
    assertThat(count("users")).isZero();assertThat(count("invoices")).isZero();assertThat(count("payments")).isZero();assertThat(count("accounting_archive")).isEqualTo(1);
  }
  @Test void expiryPausesOnlyExcessLinksAndNeverDeletesWithoutNotice() {
    for(int i=0;i<7;i++) link("excess"+i,"https://target.example/"+i);
    expired();lifecycle(NOW.plus(Duration.ofDays(32)));
    assertThat(dsl.fetchOne("SELECT count(*) FROM links WHERE status='disabled'").get(0,Long.class)).isEqualTo(2);
    lifecycle(NOW.plus(Duration.ofDays(100)));
    assertThat(count("links")).isEqualTo(7);
    new PostgresQuotaLifecycleAdapter(dsl).notified(ws.id(),NOW.plus(Duration.ofDays(100)));
    lifecycle(NOW.plus(Duration.ofDays(106)));assertThat(count("links")).isEqualTo(7);
    lifecycle(NOW.plus(Duration.ofDays(108)));assertThat(count("links")).isEqualTo(5);assertThat(count("slugs")).isEqualTo(5);
  }
  @Test void renewalRestoresQuotaPausedLinksAndCancelsDeletion() {
    for(int i=0;i<7;i++)link("renew"+i,"https://target.example/"+i);
    expired();lifecycle(NOW.plus(Duration.ofDays(32)));
    dsl.execute("UPDATE workspaces SET plan_code='pro' WHERE id=?",ws.id());
    new PostgresSubscriptionAdapter(dsl).save(Subscription.none(ws.id(),NOW).extend("pro",BillingCycle.MONTHLY,4999,NOW));
    lifecycle(NOW.plus(Duration.ofDays(100)));
    assertThat(count("links")).isEqualTo(7);assertThat(dsl.fetchOne("SELECT count(*) FROM links WHERE status='active'").get(0,Long.class)).isEqualTo(7);
    assertThat(dsl.fetchOne("SELECT count(*) FROM links WHERE quota_exceeded_at IS NOT NULL").get(0,Long.class)).isZero();
  }
}
