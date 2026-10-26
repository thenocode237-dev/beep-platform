package io.beep.bo.adapter.out.postgres;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
class ConsoleIT {
  static DSLContext db;static final Instant NOW=Instant.parse("2026-10-25T12:00:00Z");PostgresConsoleAdapter data;UUID staff;
  @BeforeAll static void setup(){var pg=new PGSimpleDataSource();String url=System.getenv("TEST_DATABASE_URL");if(url==null||url.isBlank()){var c=new PostgreSQLContainer<>("postgres:16-alpine");c.start();url=c.getJdbcUrl();pg.setUser(c.getUsername());pg.setPassword(c.getPassword());}pg.setUrl(url);var f=Flyway.configure().dataSource(pg).cleanDisabled(false).load();f.clean();f.migrate();f.validate();db=DSL.using(pg,SQLDialect.POSTGRES);}
  UUID staff(String role){UUID id=UUID.randomUUID();db.execute("INSERT INTO staff_users(id,email,password_hash,role,active,created_at) VALUES(?,?,?, ?,true,now())",id,id+"@example.org","hash",role);return id;}
  @BeforeEach void init(){db.execute("TRUNCATE staff_users CASCADE");data=new PostgresConsoleAdapter(db);staff=staff("superadmin");}
  Map<String,Object> draft(int version){return Map.of("kind","blog","slug","hello","locale","fr","title","Hello","body","Text","version",version);}
  @Test void staleEditsCannotOverwriteContent(){var a=data.saveDraft(staff,draft(0),NOW);UUID id=UUID.fromString(a.get("id").toString());data.saveDraft(staff,draft(1),NOW.plusSeconds(1));assertThatThrownBy(()->data.saveDraft(staff,draft(1),NOW.plusSeconds(2))).hasMessage("conflict");assertThatThrownBy(()->data.markPublished(id,1,1,"published",NOW)).hasMessage("conflict");assertThat(data.draft(id).get("version")).isEqualTo(2);}
  @Test void publicationsAndWithdrawalHaveMonotonicVersions(){var a=data.saveDraft(staff,draft(0),NOW);UUID id=UUID.fromString(a.get("id").toString());data.markPublished(id,1,1,"published",NOW);data.markPublished(id,1,2,"withdrawn",NOW.plusSeconds(1));assertThat(data.draft(id)).containsEntry("published_version",2).containsEntry("version",2).containsEntry("status","withdrawn");}
  @Test void impersonationExpiresAndCannotCrossStaffAccounts(){UUID w=UUID.randomUUID(),other=staff("support");data.impersonation("hash",staff,w,NOW.plusSeconds(600));assertThat(data.impersonated("hash",staff,NOW)).isEqualTo(w);assertThatThrownBy(()->data.impersonated("hash",other,NOW)).hasMessage("not_found");assertThatThrownBy(()->data.impersonated("hash",staff,NOW.plusSeconds(600))).hasMessage("not_found");}
  @Test void roleChangeRevokesSessionsAndReadonlyViews(){UUID other=staff("support");db.execute("INSERT INTO staff_sessions VALUES(?,?,?,now(),now(),now()+interval '1 hour')",UUID.randomUUID(),other,"session-hash");data.impersonation("view-hash",other,UUID.randomUUID(),NOW.plusSeconds(600));data.staffRole(staff,other,"readonly",true);assertThat(db.fetchValue("SELECT count(*) FROM staff_sessions WHERE staff_id=?",other)).isEqualTo(0L);assertThatThrownBy(()->data.impersonated("view-hash",other,NOW)).hasMessage("not_found");}
  @Test void staffCannotRemoveTheLastAdministratorOrEditThemself(){UUID other=staff("support");assertThatThrownBy(()->data.staffRole(other,staff,"readonly",true)).hasMessage("forbidden");assertThatThrownBy(()->data.staffRole(staff,staff,"readonly",false)).hasMessage("forbidden");}
  @Test void orphanedWorkspaceCleanupRemovesNotesAndReadonlyTokens(){UUID w=UUID.randomUUID();data.note(w,staff,"Private note",NOW);data.impersonation("hash",staff,w,NOW.plusSeconds(600));assertThat(data.workspaceReferences(null,100)).containsExactly(w);data.purgeWorkspace(w);assertThat(data.notes(w)).isEmpty();assertThat(data.workspaceReferences(null,100)).isEmpty();}
  @Test void enterpriseStaffReviewCapturesRolesAndIsAppendOnly(){var enterprise=new PostgresEnterpriseConsoleAdapter(db);enterprise.review(staff,"approved","Quarterly review",NOW);assertThat(enterprise.reviews()).singleElement().satisfies(r->{assertThat(r.get("snapshot").toString()).contains(staff.toString()).contains("superadmin");assertThat(r.get("due_at").toString()).startsWith("2027-01-23");});assertThatThrownBy(()->db.execute("UPDATE staff_access_reviews SET decision='remediation_required'")).hasMessageContaining("append only");assertThatThrownBy(()->db.execute("TRUNCATE staff_access_reviews")).hasMessageContaining("append only");}
  @Test void editorialContentSupportsSpanishAndPortuguese(){for(String language:List.of("es","pt")){var b=new HashMap<>(draft(0));b.put("locale",language);data.saveDraft(staff,b,NOW);}assertThat(data.drafts()).hasSize(2);}
}
