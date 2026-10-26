package io.beep.bo.app.enterprise;
import io.beep.bo.app.port.*;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.domain.staff.*;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.Clock;
import java.util.*;
public class EnterpriseConsoleUseCase {
 private final CoreGatewayPort core;private final EnterpriseConsoleRepository data;private final AuditPort audit;private final StepUp step;private final Clock clock;
 public EnterpriseConsoleUseCase(CoreGatewayPort core,EnterpriseConsoleRepository data,AuditPort audit,StepUp step,Clock clock){this.core=core;this.data=data;this.audit=audit;this.step=step;this.clock=clock;}
 public Map<String,Object> overview(StaffUser u){u.require(Permission.ENTERPRISE_READ);var result=new LinkedHashMap<>(core.ecosystem("GET","/enterprise/overview",Map.of(),u.id()));if(u.role().can(Permission.STAFF_MANAGE))result.put("staffReviews",data.reviews());return result;}
 public Map<String,Object> workspace(StaffUser u,UUID id){u.require(Permission.ENTERPRISE_READ);return core.ecosystem("GET","/enterprise/workspaces/"+id,Map.of(),u.id());}
 public Map<String,Object> action(StaffUser u,String action,UUID workspace,Map<String,Object> body,String ip){Permission permission=switch(action){case "contract"->Permission.ENTERPRISE_WRITE;case "sdk-releases"->Permission.SDK_WRITE;case "evidence","access-reviews"->Permission.COMPLIANCE_WRITE;case "staff-reviews"->Permission.STAFF_MANAGE;case "failovers"->Permission.OPERATIONS_WRITE;default->throw new DomainException("invalid_request");};u.require(permission);String reason=Objects.toString(body.get("reason"),"").strip();if(reason.length()<3||reason.length()>1000)throw new DomainException("invalid_reason");step.require(u,Objects.toString(body.get("totp"),""));var payload=new LinkedHashMap<>(body);payload.remove("totp");Map<String,Object> result;if(action.equals("staff-reviews")){String decision=Objects.toString(body.get("decision"),"");if(!Set.of("approved","remediation_required").contains(decision))throw new DomainException("invalid_request");data.review(u.id(),decision,reason,clock.instant());result=Map.of("saved",true);}else{String route=action.equals("contract")?"/enterprise/workspaces/"+Objects.requireNonNull(workspace):"/enterprise/"+action;result=core.ecosystem(action.equals("contract")?"PUT":"POST",route,payload,u.id());}audit.append(new AuditEvent(clock.instant(),u.id(),u.email().value(),"enterprise."+action,workspace==null?action:workspace.toString(),reason,ip));return result;}
}
