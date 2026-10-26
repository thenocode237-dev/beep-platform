package io.beep.bo.app;
import static org.assertj.core.api.Assertions.*;
import io.beep.bo.app.ecosystem.EcosystemConsoleUseCase;
import io.beep.bo.app.port.*;
import io.beep.bo.domain.staff.*;
import io.beep.bo.domain.DomainException;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class EcosystemConsoleTest {
  final ModulesTest fixture=new ModulesTest();final UUID workspace=UUID.randomUUID();final Map<String,Object> session=new HashMap<>();
  final FakeCore gateway=new FakeCore(){@Override public Map<String,Object> ecosystem(String method,String path,Map<String,Object> body,UUID staff){calls.add(method+":"+path);return Map.of("items",List.of(),"links",List.of(Map.of("slug","example")));}};
  final ConsoleRepository data=(ConsoleRepository)Proxy.newProxyInstance(ConsoleRepository.class.getClassLoader(),new Class<?>[]{ConsoleRepository.class},(p,m,args)->{switch(m.getName()){case "impersonation":session.put("hash",args[0]);session.put("staff",args[1]);session.put("workspace",args[2]);session.put("expires",args[3]);return null;case "impersonated":if(!Objects.equals(session.get("hash"),args[0])||!Objects.equals(session.get("staff"),args[1])||!((Instant)session.get("expires")).isAfter((Instant)args[2]))throw new DomainException("not_found");return session.get("workspace");case "revokeImpersonation":session.clear();return null;default:throw new AssertionError("unexpected local write: "+m.getName());}});
  final TokenPort tokens=new TokenPort(){public String newToken(){return "random-test-token";}public String hash(String s){return "hash:"+s;}};
  final EcosystemConsoleUseCase use=new EcosystemConsoleUseCase(gateway,data,fixture.auditPort,fixture.stepUp,tokens,fixture.clock);
  @Test void financeCannotImpersonateAndSupportCannotPublishOrPay(){assertThatThrownBy(()->use.action(fixture.staff(Role.FINANCE),workspace,Map.of("action","impersonate","reason","investigate"),fixture.code(),"ip")).hasMessage("forbidden");assertThatThrownBy(()->use.publish(fixture.staff(Role.SUPPORT),UUID.randomUUID(),"published","publish article",fixture.code(),"ip")).hasMessage("forbidden");assertThatThrownBy(()->use.payout(fixture.staff(Role.SUPPORT),Map.of("reason","pay"),fixture.code(),"ip")).hasMessage("forbidden");assertThat(gateway.calls).isEmpty();}
  @Test void totpIsCheckedBeforeOpeningTheWorkspace(){assertThatThrownBy(()->use.action(fixture.staff(Role.SUPPORT),workspace,Map.of("action","impersonate","reason","investigate"),"000000","ip")).hasMessage("reauth_required");assertThat(session).isEmpty();assertThat(gateway.calls).isEmpty();}
  @Test void readonlySessionIsActorBoundHasTenMinuteTtlAndNeverDelegatesAMutation(){var actor=fixture.staff(Role.SUPPORT);var result=use.action(actor,workspace,Map.of("action","impersonate","reason","support ticket"),fixture.code(),"ip");assertThat(result.get("readOnly")).isEqualTo(true);assertThat(result.get("expiresAt")).isEqualTo(fixture.clock.instant().plusSeconds(600));assertThat(use.impersonation(actor,result.get("token").toString()).get("readOnly")).isEqualTo(true);assertThatThrownBy(()->use.impersonation(fixture.staff(Role.SUPPORT),result.get("token").toString())).hasMessage("not_found");assertThat(gateway.calls).containsExactly("POST:/ecosystem/workspaces/"+workspace+"/actions","GET:/ecosystem/workspaces/"+workspace);assertThat(fixture.audit).singleElement().satisfies(e->assertThat(e.action()).isEqualTo("ecosystem.impersonate"));use.stopImpersonation(actor,result.get("token").toString());assertThatThrownBy(()->use.impersonation(actor,result.get("token").toString())).isInstanceOf(RuntimeException.class);}
  @Test void granularStaffRolesHaveNoCrossDepartmentWritePrivileges(){assertThat(Role.SUPPORT.can(Permission.AFFILIATES_PAY)).isFalse();assertThat(Role.CONTENT.can(Permission.INTEGRATIONS_WRITE)).isFalse();assertThat(Role.FINANCE.can(Permission.STAFF_MANAGE)).isFalse();assertThat(Role.TRUST_SAFETY.can(Permission.CONTENT_PUBLISH)).isFalse();assertThat(Role.READONLY.can(Permission.IMPERSONATE_READ)).isFalse();}
}
