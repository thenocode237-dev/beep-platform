package io.beep.bo.app.developer;
import io.beep.bo.app.port.*;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.domain.staff.*;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.DomainException;
import java.time.Clock;
import java.util.*;
public class DeveloperConsoleUseCase {
  private final CoreGatewayPort core;private final AuditPort audit;private final StepUp step;private final Clock clock;
  public DeveloperConsoleUseCase(CoreGatewayPort core,AuditPort audit,StepUp step,Clock clock){this.core=core;this.audit=audit;this.step=step;this.clock=clock;}
  public Map<String,Object> monitor(StaffUser actor){actor.require(Permission.METRICS_READ);return core.developerMonitor(actor.id());}
  public Map<String,Object> workspace(StaffUser actor,UUID ws){actor.require(Permission.USERS_READ);return core.developerWorkspace(ws,actor.id());}
  public Map<String,Object> action(StaffUser actor,UUID ws,Map<String,Object> r,String totp,String ip){
    String action=Objects.toString(r.get("action"),""),reason=Objects.toString(r.get("reason"),"");
    actor.require(switch(action){case "revoke"->Permission.API_KEYS_REVOKE;case "replay"->Permission.WEBHOOKS_REPLAY;case "limits"->Permission.ENTITLEMENTS_WRITE;case "visitor.export"->Permission.PRIVACY_EXPORT;case "visitor.erase"->Permission.PRIVACY_ERASE;default->throw new DomainException("invalid_request");});
    if(reason.strip().length()<3||reason.length()>1000)throw new DomainException("invalid_reason");step.require(actor,totp);
    var result=core.developerAction(ws,r,actor.id());audit.append(new AuditEvent(clock.instant(),actor.id(),actor.email().value(),"developer."+action,ws.toString(),reason,ip));return result;
  }
}
