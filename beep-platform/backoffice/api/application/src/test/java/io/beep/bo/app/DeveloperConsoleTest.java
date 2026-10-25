package io.beep.bo.app;
import static org.assertj.core.api.Assertions.*;
import io.beep.bo.app.developer.DeveloperConsoleUseCase;
import io.beep.bo.domain.staff.Role;
import java.util.*;
import org.junit.jupiter.api.Test;
class DeveloperConsoleTest {
 final ModulesTest fixture=new ModulesTest();
 final FakeCore gateway=new FakeCore(){@Override public Map<String,Object> developerAction(UUID ws,Map<String,Object> action,UUID staff){calls.add(action.get("action")+":"+staff);return Map.of("ok",true);}};
 final DeveloperConsoleUseCase use=new DeveloperConsoleUseCase(gateway,fixture.auditPort,fixture.stepUp,fixture.clock);
 final UUID workspace=UUID.randomUUID();
 @Test void readonlyCannotRevokeKeysOrReplayDeliveries(){for(String action:List.of("revoke","replay"))assertThatThrownBy(()->use.action(fixture.staff(Role.READONLY),workspace,Map.of("action",action,"reason","test reason"),fixture.code(),"ip")).hasMessageContaining("forbidden");assertThat(gateway.calls).isEmpty();assertThat(fixture.audit).isEmpty();}
 @Test void stepUpIsRequiredBeforeAnyMutation(){assertThatThrownBy(()->use.action(fixture.staff(Role.SUPERADMIN),workspace,Map.of("action","revoke","reason","test reason"),"000000","ip")).hasMessageContaining("reauth_required");assertThat(gateway.calls).isEmpty();}
 @Test void acceptedActionIsForwardedAndAudited(){var actor=fixture.staff(Role.SUPERADMIN);use.action(actor,workspace,Map.of("action","replay","reason","support request"),fixture.code(),"ip");assertThat(gateway.calls).containsExactly("replay:"+actor.id());assertThat(fixture.audit).singleElement().satisfies(a->{assertThat(a.action()).isEqualTo("developer.replay");assertThat(a.target()).isEqualTo(workspace.toString());});}
}
