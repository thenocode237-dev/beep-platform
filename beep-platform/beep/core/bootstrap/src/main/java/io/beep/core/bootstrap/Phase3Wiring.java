package io.beep.core.bootstrap;
import io.beep.core.app.port.*;
import io.beep.core.app.conversion.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.adapter.out.postgres.*;
import io.beep.core.adapter.out.security.PinnedWebhookTransportAdapter;
import org.jooq.DSLContext;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@Profile("!migrate")
public class Phase3Wiring {
  @Bean DeveloperRepository developerRepository(DSLContext db){return new PostgresDeveloperAdapter(db);}
  @Bean ClickIds clickIds(@Value("${BEEP_CONVERSION_SECRET:${beep.totp-encryption-key}}") String secret){return new ClickIds(secret);}
  @Bean DeveloperUseCase developer(DeveloperRepository data,Workspaces workspaces,WorkspaceRepository ws,UserRepository users,LinkRepository links,TokenPort tokens,SecretCipherPort cipher,TransactionPort tx,ClickIds ids,Clock clock){return new DeveloperUseCase(data,workspaces,ws,users,links,tokens,cipher,tx,ids,clock);}
  @Bean(destroyMethod="close") AsyncConversionTrackerAdapter conversionTracker(DeveloperRepository data,ClickIds ids,WorkspaceRepository ws,Workspaces workspaces,TransactionPort tx,@Value("${beep.public-url}") String url,MeterRegistry registry){var tracker=new AsyncConversionTrackerAdapter(data,ids,ws,workspaces,tx,url);registry.gauge("conversion.queue",tracker,AsyncConversionTrackerAdapter::queued);registry.gauge("conversion.dropped",tracker,t->t.dropped.get());return tracker;}
  @Bean WebhookTransportPort webhookTransport(){return new PinnedWebhookTransportAdapter();}
  @Bean AdminDeveloperUseCase adminDeveloper(DeveloperUseCase developer,DeveloperRepository data,io.beep.core.app.admin.AdminEntitlementsUseCase entitlements,TrustRepository audit,TransactionPort tx,Clock clock){return new AdminDeveloperUseCase(developer,data,entitlements,audit,tx,clock);}
  @Bean WebhookWorker webhookWorker(DeveloperRepository data,SecretCipherPort cipher,WebhookTransportPort http,TransactionPort tx,Clock clock,DeveloperUseCase developer){return new WebhookWorker(data,cipher,http,tx,clock,developer);}
}
