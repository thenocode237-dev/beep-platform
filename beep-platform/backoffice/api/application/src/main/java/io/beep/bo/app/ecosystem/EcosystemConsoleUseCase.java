package io.beep.bo.app.ecosystem;
import io.beep.bo.app.port.*;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.domain.staff.*;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.*;
import java.util.*;

public class EcosystemConsoleUseCase {
  private final CoreGatewayPort core;private final ConsoleRepository data;private final AuditPort audit;private final StepUp step;private final TokenPort tokens;private final Clock clock;
  public EcosystemConsoleUseCase(CoreGatewayPort core,ConsoleRepository data,AuditPort audit,StepUp step,TokenPort tokens,Clock clock){this.core=core;this.data=data;this.audit=audit;this.step=step;this.tokens=tokens;this.clock=clock;}
  private void log(StaffUser u,String action,String target,String reason,String ip){audit.append(new AuditEvent(clock.instant(),u.id(),u.email().value(),action,target,reason,ip));}
  private String reason(Map<String,Object> b){String r=Objects.toString(b.get("reason"),"").strip();if(r.length()<3||r.length()>1000)throw new DomainException("invalid_reason");return r;}
  public Map<String,Object> monitor(StaffUser u){u.require(Permission.METRICS_READ);return core.ecosystem("GET","/ecosystem/monitor",Map.of(),u.id());}
  public Map<String,Object> workspace(StaffUser u,UUID w){u.require(Permission.INTEGRATIONS_READ);var out=new LinkedHashMap<>(core.ecosystem("GET","/ecosystem/workspaces/"+w,Map.of(),u.id()));if(u.role().can(Permission.SUPPORT_NOTES))out.put("notes",data.notes(w));return out;}
  public Map<String,Object> action(StaffUser u,UUID w,Map<String,Object> b,String totp,String ip){String action=Objects.toString(b.get("action"),""),r=reason(b);u.require(switch(action){case "integration.disable","integration.replay"->Permission.INTEGRATIONS_WRITE;case "impersonate"->Permission.IMPERSONATE_READ;case "note"->Permission.SUPPORT_NOTES;default->throw new DomainException("invalid_request");});step.require(u,totp);
    if(action.equals("note")){String text=Objects.toString(b.get("text"),"").strip();if(text.isEmpty()||text.length()>4000)throw new DomainException("invalid_request");core.ecosystem("GET","/ecosystem/workspaces/"+w,Map.of(),u.id());data.note(w,u.id(),text,clock.instant());log(u,"support.note",w.toString(),r,ip);return Map.of("saved",true);}
    core.ecosystem("POST","/ecosystem/workspaces/"+w+"/actions",b,u.id());log(u,"ecosystem."+action,w.toString(),r,ip);
    if(action.equals("impersonate")){String raw=tokens.newToken();Instant expiry=clock.instant().plus(Duration.ofMinutes(10));data.impersonation(tokens.hash(raw),u.id(),w,expiry);return Map.of("token",raw,"expiresAt",expiry,"readOnly",true);}
    return Map.of("saved",true);
  }
  public Map<String,Object> impersonation(StaffUser u,String token){u.require(Permission.IMPERSONATE_READ);UUID w=data.impersonated(tokens.hash(token),u.id(),clock.instant());return Map.of("readOnly",true,"workspace",w,"view",core.ecosystem("GET","/ecosystem/workspaces/"+w,Map.of(),u.id()));}
  public void stopImpersonation(StaffUser u,String token){u.require(Permission.IMPERSONATE_READ);data.revokeImpersonation(tokens.hash(token),u.id());}
  public Map<String,Object> affiliates(StaffUser u){u.require(Permission.AFFILIATES_READ);return core.ecosystem("GET","/ecosystem/affiliates",Map.of(),u.id());}
  public Map<String,Object> payout(StaffUser u,Map<String,Object> b,String totp,String ip){u.require(Permission.AFFILIATES_PAY);step.require(u,totp);String r=reason(b);var result=core.ecosystem("POST","/ecosystem/affiliates/actions",b,u.id());log(u,"affiliate.paid",Objects.toString(b.get("commissions")),r,ip);return result;}
  public Map<String,Object> drafts(StaffUser u){u.require(Permission.CONTENT_EDIT);return Map.of("items",data.drafts(),"published",core.ecosystem("GET","/content",Map.of(),u.id()).get("items"));}
  public Map<String,Object> save(StaffUser u,Map<String,Object> b,String ip){u.require(Permission.CONTENT_EDIT);validateContent(b);var candidate=new LinkedHashMap<>(b);if(!b.containsKey("version")||!(b.get("version") instanceof Number n)||n.intValue()==0){Object items=core.ecosystem("GET","/content",Map.of(),u.id()).get("items");if(items instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> row&&Objects.equals(row.get("kind"),b.get("kind"))&&Objects.equals(row.get("slug"),b.get("slug"))&&Objects.equals(row.get("locale"),b.get("locale")))candidate.put("initialVersion",((Number)row.get("version")).intValue()+1);}var result=data.saveDraft(u.id(),candidate,clock.instant());log(u,"content.draft",Objects.toString(result.get("id")),Objects.toString(b.get("title")),ip);return result;}
  private void validateContent(Map<String,Object> b){if(!Set.of("blog","roadmap","announcement").contains(Objects.toString(b.get("kind")))||!Objects.toString(b.get("slug"),"").matches("[a-z0-9][a-z0-9-]{0,79}")||!Set.of("fr","en","es","pt").contains(Objects.toString(b.get("locale")))||Objects.toString(b.get("title"),"").isBlank()||Objects.toString(b.get("title"),"").length()>200||Objects.toString(b.get("body"),"").length()>50000)throw new DomainException("invalid_request");}
  public Map<String,Object> publish(StaffUser u,UUID id,String status,String why,String totp,String ip){u.require(Permission.CONTENT_PUBLISH);step.require(u,totp);String r=reason(Map.of("reason",why));if(!Set.of("published","withdrawn").contains(status))throw new DomainException("invalid_request");var draft=new LinkedHashMap<>(data.draft(id));int expected=((Number)draft.get("version")).intValue(),published=Math.max(expected,((Number)draft.getOrDefault("published_version",0)).intValue()+1);draft.put("version",published);draft.put("status",status);draft.put("reason",r);var result=core.ecosystem("POST","/content/publications",draft,u.id());data.markPublished(id,expected,published,status,clock.instant());log(u,"content."+status,id.toString(),r,ip);return result;}
  public List<Map<String,Object>> staff(StaffUser u){u.require(Permission.STAFF_MANAGE);return data.staff();}
  public void staffRole(StaffUser u,UUID target,String role,boolean active,String why,String totp,String ip){u.require(Permission.STAFF_MANAGE);step.require(u,totp);String r=reason(Map.of("reason",why));Role parsed;try{parsed=Role.fromDb(role);}catch(Exception e){throw new DomainException("invalid_request");}data.staffRole(u.id(),target,parsed.db(),active);log(u,"staff.role",target.toString(),r+" · "+role+" · "+active,ip);}
}
