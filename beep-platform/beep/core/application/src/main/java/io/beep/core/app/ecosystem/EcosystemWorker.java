package io.beep.core.app.ecosystem;

import io.beep.core.app.port.*;
import io.beep.core.app.conversion.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import java.time.Clock;
import java.util.concurrent.*;

public class EcosystemWorker {
  private final EcosystemRepository data;private final DeveloperUseCase conversions;private final DestinationProbePort probe;
  private final WebhookTransportPort http;private final SecretCipherPort cipher;private final MailPort mail;
  private final WorkspaceRepository workspaces;private final Workspaces rights;private final UserRepository users;private final TransactionPort tx;private final Clock clock;
  public EcosystemWorker(EcosystemRepository data,DeveloperUseCase conversions,DestinationProbePort probe,WebhookTransportPort http,SecretCipherPort cipher,MailPort mail,WorkspaceRepository workspaces,Workspaces rights,UserRepository users,TransactionPort tx,Clock clock){this.data=data;this.conversions=conversions;this.probe=probe;this.http=http;this.cipher=cipher;this.mail=mail;this.workspaces=workspaces;this.rights=rights;this.users=users;this.tx=tx;this.clock=clock;}
  public String incoming(){var due=tx.run(()->data.claimIncoming(clock.instant(),50));for(var e:due){try{tx.run(()->{workspaces.lock(e.workspace());if(!data.incomingActive(e.workspace(),e.id(),e.attempts()))return;var i=data.integration(e.integration()).orElseThrow(()->new DomainException("not_found"));var w=workspaces.findById(e.workspace()).orElseThrow(()->new DomainException("not_found"));w.requireWritable();if(i.status().equals("disabled")||!rights.entitlements(w).enabled("features.integrations"))throw new DomainException("feature_unavailable");conversions.collectPseudonymous(e.workspace(),e.command());data.incomingResult(e.id(),e.attempts(),null,clock.instant());data.integrationError(e.integration(),null,clock.instant());});}catch(RuntimeException x){String error=x instanceof DomainException d?d.code():"processing_failed";tx.run(()->{data.incomingResult(e.id(),e.attempts(),error,clock.instant());data.integrationError(e.integration(),error,clock.instant());});}}return "events="+due.size();}
  public String health(){var due=tx.run(()->data.claimProbes(clock.instant(),50));try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var gate=new Semaphore(5);var tasks=due.stream().map(p->pool.submit(()->{try{gate.acquire();int status;try{status=probe.probe(p.url());}catch(Exception e){status=0;}data.probeResult(p.link(),p.url(),status,clock.instant());}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{gate.release();}})).toList();for(var t:tasks)try{t.get();}catch(Exception e){throw new IllegalStateException(e);}}return "probes="+due.size();}
  public String radar(){tx.run(()->data.evaluateRadar(clock.instant()));return "evaluated";}
  public String reports(){tx.run(()->data.scheduleReports(clock.instant()));return "scheduled";}
  public String affiliates(){tx.run(()->data.accrueCommissions(clock.instant()));return "accrued";}
  public String retention(){tx.run(()->data.retention(clock.instant()));return "purged";}
  public String notifications(){var due=tx.run(()->data.claimTasks(clock.instant(),50));for(var t:due){String error=null;try{
    var w=workspaces.findById(t.workspace()).orElseThrow(()->new DomainException("not_found"));w.requireWritable();var e=rights.entitlements(w);if(!e.enabled("features.radar")&&!e.enabled("features.reports"))throw new DomainException("feature_unavailable");
    if(t.channel().equals("email")){var owner=users.findById(w.ownerId()).filter(u->u.canLogin()).orElseThrow(()->new DomainException("not_found"));mail.sendWorkspaceMessage(owner.email(),owner.locale(),t.body().startsWith("Rapport")?"Rapport Beepclick":"Radar Beepclick",t.body(),"/app/radar");}
    else{if(!e.enabled("features.insights"))throw new DomainException("feature_unavailable");String destination=cipher.decrypt(t.destination()),payload="{\"text\":"+quote(t.body())+"}";if(!t.channel().equals("slack"))throw new DomainException("invalid_channel");int status=http.deliver(destination,payload,"",t.id().toString());if(status<200||status>=300)throw new DomainException("http_"+status);}
  }catch(DomainException x){error=x.code();}catch(Exception x){error="delivery_failed";}data.taskResult(t.id(),t.attempts(),error,clock.instant());}return "notifications="+due.size();}
  static String quote(String text){StringBuilder b=new StringBuilder("\"");for(char c:text.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();}
}
