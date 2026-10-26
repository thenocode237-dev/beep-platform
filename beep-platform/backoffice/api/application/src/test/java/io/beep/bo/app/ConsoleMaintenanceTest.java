package io.beep.bo.app;
import static org.assertj.core.api.Assertions.*;
import io.beep.bo.app.ecosystem.ConsoleMaintenanceUseCase;
import io.beep.bo.app.port.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
class ConsoleMaintenanceTest {
  final ModulesTest fixture=new ModulesTest();final UUID workspace=UUID.randomUUID();final List<UUID> purged=new ArrayList<>();int status=404;
  final ConsoleRepository data=(ConsoleRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ConsoleRepository.class},(p,m,a)->switch(m.getName()){case "expireViews"->null;case "workspaceReferences"->List.of(workspace);case "purgeWorkspace"->{purged.add((UUID)a[0]);yield null;}default->throw new AssertionError(m.getName());});
  final FakeCore gateway=new FakeCore(){@Override public Map<String,Object> ecosystem(String method,String path,Map<String,Object> body,UUID staff){if(status!=200)throw new CoreRejectedException(status,"test");return Map.of("exists",true);}};
  final ConsoleMaintenanceUseCase use=new ConsoleMaintenanceUseCase(data,gateway,fixture.auditPort,fixture.clock);
  @Test void confirmedErasureRemovesLocalSupportDataAndAuditsOnlyTheIdentifier(){assertThat(use.run()).isEqualTo("removed=1");assertThat(purged).containsExactly(workspace);assertThat(fixture.audit).singleElement().satisfies(e->{assertThat(e.action()).isEqualTo("privacy.workspace.cleanup");assertThat(e.target()).isEqualTo(workspace.toString());});}
  @Test void anUpstreamFailureNeverErasesSupportNotes(){status=503;assertThatThrownBy(()->use.run()).isInstanceOf(CoreGatewayPort.CoreRejectedException.class);assertThat(purged).isEmpty();assertThat(fixture.audit).isEmpty();}
  @Test void existingWorkspacesArePreserved(){status=200;assertThat(use.run()).isEqualTo("removed=0");assertThat(purged).isEmpty();}
}
