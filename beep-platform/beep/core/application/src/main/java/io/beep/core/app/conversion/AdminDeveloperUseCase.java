package io.beep.core.app.conversion;
import io.beep.core.app.port.*;
import io.beep.core.app.admin.AdminEntitlementsUseCase;
import io.beep.core.domain.DomainException;
import java.time.Clock;
import java.util.*;
public class AdminDeveloperUseCase {
  public record Action(String action,String id,String reason,Long rpm,Long events) {}
  private final DeveloperUseCase developer;private final DeveloperRepository data;private final AdminEntitlementsUseCase entitlements;private final TrustRepository audit;private final TransactionPort tx;private final Clock clock;
  public AdminDeveloperUseCase(DeveloperUseCase developer,DeveloperRepository data,AdminEntitlementsUseCase entitlements,TrustRepository audit,TransactionPort tx,Clock clock){this.developer=developer;this.data=data;this.entitlements=entitlements;this.audit=audit;this.tx=tx;this.clock=clock;}
  public Map<String,Object> monitor(){return data.monitor();}
  public Map<String,Object> overview(UUID ws){return developer.overview(ws);}
  public Map<String,Object> action(UUID ws,Action r,String staff){
    if(r.reason()==null||r.reason().strip().length()<3||r.reason().length()>1000||staff==null||staff.isBlank())throw new DomainException("invalid_request");
    return tx.run(()->{data.lock(ws);Map<String,Object> result=Map.of("ok",true);
      switch(Objects.toString(r.action(),"")){
        case "revoke"->data.revoke(ws,uuid(r.id()),clock.instant());
        case "replay"->data.replay(ws,uuid(r.id()),clock.instant());
        case "limits"->{if(r.rpm()==null||r.events()==null)throw new DomainException("invalid_request");entitlements.setOverrides(ws,Map.of("api.rpm",r.rpm(),"events.monthly_max",r.events()),r.reason(),staff);}
        case "visitor.export","visitor.erase"->{if(r.id()==null||!r.id().matches("[0-9a-f]{64}"))throw new DomainException("invalid_request");if(r.action().equals("visitor.export"))result=Map.of("visitor",r.id(),"events",data.visitor(ws,r.id()));else data.eraseVisitor(ws,r.id());}
        default->throw new DomainException("invalid_request");
      }
      audit.audit(staff,"developer."+r.action(),ws.toString(),r.reason());return result;
    });
  }
  private static UUID uuid(String value){try{return UUID.fromString(value);}catch(RuntimeException e){throw new DomainException("invalid_request");}}
}
