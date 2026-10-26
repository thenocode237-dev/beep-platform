package io.beep.bo.app.ecosystem;
import io.beep.bo.app.port.*;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.Clock;
import java.util.*;

/** Reconciles local support data through the core API, including self-service account erasure. */
public class ConsoleMaintenanceUseCase {
  private static final UUID SYSTEM=new UUID(0,0);
  private final ConsoleRepository data;private final CoreGatewayPort core;private final AuditPort audit;private final Clock clock;
  private UUID cursor;
  public ConsoleMaintenanceUseCase(ConsoleRepository data,CoreGatewayPort core,AuditPort audit,Clock clock){this.data=data;this.core=core;this.audit=audit;this.clock=clock;}
  public synchronized String run(){data.expireViews(clock.instant());var workspaces=data.workspaceReferences(cursor,100);if(workspaces.isEmpty()){cursor=null;workspaces=data.workspaceReferences(null,100);}int removed=0;
    for(UUID id:workspaces){try{core.ecosystem("GET","/ecosystem/workspaces/"+id,Map.of(),SYSTEM);}catch(CoreGatewayPort.CoreRejectedException e){if(e.status()!=404)throw e;data.purgeWorkspace(id);audit.append(new AuditEvent(clock.instant(),null,"system","privacy.workspace.cleanup",id.toString(),"Workspace removed from core",null));removed++;}cursor=id;}
    return "removed="+removed;
  }
}
