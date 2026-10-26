package io.beep.bo.app;
import static org.assertj.core.api.Assertions.*;
import io.beep.bo.app.enterprise.EnterpriseConsoleUseCase;
import io.beep.bo.app.port.*;
import io.beep.bo.domain.staff.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
class EnterpriseConsoleTest {
 final ModulesTest fixture=new ModulesTest();final UUID workspace=UUID.randomUUID();int reviews;Map<String,Object> payload;
 final FakeCore core=new FakeCore(){@Override public Map<String,Object> ecosystem(String method,String path,Map<String,Object> body,UUID staff){calls.add(method+":"+path);payload=body;return Map.of("saved",true);}};
 final EnterpriseConsoleRepository data=(EnterpriseConsoleRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{EnterpriseConsoleRepository.class},(p,m,a)->{if(m.getName().equals("review")){reviews++;return null;}return List.of();});
 final EnterpriseConsoleUseCase service=new EnterpriseConsoleUseCase(core,data,fixture.auditPort,fixture.stepUp,fixture.clock);
 Map<String,Object> body(String code){return Map.of("reason","Quarterly review","totp",code,"decision","approved");}
 @Test void contractChangesRequireDedicatedPermission(){for(Role role:List.of(Role.SUPPORT,Role.FINANCE,Role.CONTENT,Role.READONLY,Role.TRUST_SAFETY))assertThatThrownBy(()->service.action(fixture.staff(role),"contract",workspace,body(fixture.code()),"ip")).hasMessage("forbidden");assertThat(core.calls).isEmpty();}
 @Test void stepUpPrecedesCoreMutation(){assertThatThrownBy(()->service.action(fixture.staff(Role.SUPERADMIN),"contract",workspace,body("000000"),"ip")).hasMessage("reauth_required");assertThat(core.calls).isEmpty();}
 @Test void successfulChangesAreAuditedAndTotpNeverLeavesBackoffice(){service.action(fixture.staff(Role.SUPERADMIN),"contract",workspace,body(fixture.code()),"ip");assertThat(payload).doesNotContainKey("totp");assertThat(core.calls).containsExactly("PUT:/enterprise/workspaces/"+workspace);assertThat(fixture.audit).singleElement().satisfies(e->assertThat(e.action()).isEqualTo("enterprise.contract"));}
 @Test void staffReviewsStayInTheBackofficeDatabase(){service.action(fixture.staff(Role.SUPERADMIN),"staff-reviews",null,body(fixture.code()),"ip");assertThat(reviews).isEqualTo(1);assertThat(core.calls).isEmpty();}
 @Test void departmentalWritesRemainSeparated(){assertThatThrownBy(()->service.action(fixture.staff(Role.CONTENT),"evidence",null,body(fixture.code()),"ip")).hasMessage("forbidden");assertThatThrownBy(()->service.action(fixture.staff(Role.TRUST_SAFETY),"sdk-releases",null,body(fixture.code()),"ip")).hasMessage("forbidden");assertThatThrownBy(()->service.action(fixture.staff(Role.FINANCE),"failovers",null,body(fixture.code()),"ip")).hasMessage("forbidden");}
}
