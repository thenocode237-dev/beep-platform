package io.beep.core.bootstrap;
import io.beep.core.app.port.*;
import io.beep.core.app.ecosystem.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.app.conversion.DeveloperUseCase;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.adapter.out.postgres.PostgresEcosystemAdapter;
import io.beep.core.adapter.out.security.PinnedDestinationProbeAdapter;
import org.jooq.DSLContext;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration @Profile("!migrate")
public class Phase4Wiring {
  @Bean AdminEcosystemUseCase adminEcosystem(EcosystemRepository data,TrustRepository audit,TransactionPort tx,Clock clock){return new AdminEcosystemUseCase(data,audit,tx,clock);}
  @Bean EcosystemRepository ecosystemRepository(DSLContext db){return new PostgresEcosystemAdapter(db);}
  @Bean EcosystemUseCase ecosystem(EcosystemRepository data,Workspaces workspaces,WorkspaceRepository ws,TransactionPort tx,TokenPort tokens,SecretCipherPort cipher,MailPort mail,DeveloperRepository dev,CreateLinkUseCase create,Clock clock,DeveloperUseCase conversions){return new EcosystemUseCase(data,workspaces,ws,tx,tokens,cipher,mail,dev,create,clock).withConversions(conversions);}
  @Bean DestinationProbePort destinationProbe(){return new PinnedDestinationProbeAdapter();}
  @Bean EcosystemWorker ecosystemWorker(EcosystemRepository data,DeveloperUseCase conversions,DestinationProbePort probe,WebhookTransportPort http,SecretCipherPort cipher,MailPort mail,WorkspaceRepository ws,Workspaces rights,UserRepository users,TransactionPort tx,Clock clock){return new EcosystemWorker(data,conversions,probe,http,cipher,mail,ws,rights,users,tx,clock);}
}
