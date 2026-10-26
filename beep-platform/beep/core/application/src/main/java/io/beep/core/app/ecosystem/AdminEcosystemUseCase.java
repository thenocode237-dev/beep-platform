package io.beep.core.app.ecosystem;
import io.beep.core.app.port.*;
import io.beep.core.domain.DomainException;
import java.time.Clock;
import java.util.*;

public class AdminEcosystemUseCase {
  private final EcosystemRepository data;private final TrustRepository audit;private final TransactionPort tx;private final Clock clock;
  public AdminEcosystemUseCase(EcosystemRepository data,TrustRepository audit,TransactionPort tx,Clock clock){this.data=data;this.audit=audit;this.tx=tx;this.clock=clock;}
  public Map<String,Object> monitor(){return data.monitor();}
  public Map<String,Object> workspace(UUID id){return data.staffWorkspace(id);}
  public Map<String,Object> action(UUID id,Map<String,Object> body,UUID staff){String action=Objects.toString(body.get("action"),""),reason=reason(body);return tx.run(()->{
    switch(action){case "integration.disable"->data.disconnect(id,uuid(body.get("id")));case "integration.replay"->data.replayIncoming(id,uuid(body.get("id")),clock.instant());case "impersonate"->data.staffWorkspace(id);default->throw new DomainException("invalid_request");}
    audit.audit(staff.toString(),"ecosystem."+action,id.toString(),reason);return Map.of("saved",true);
  });}
  public Map<String,Object> affiliates(){return data.affiliates();}
  public Map<String,Object> payout(Map<String,Object> body,UUID staff){String reason=reason(body),reference=Objects.toString(body.get("reference"),"");if(reference.length()<3||reference.length()>200||!(body.get("commissions") instanceof List<?> list)||list.isEmpty()||list.size()>100)throw new DomainException("invalid_request");var ids=list.stream().map(AdminEcosystemUseCase::uuid).distinct().sorted().toList();tx.run(()->{data.payout(staff,ids,reference,clock.instant());audit.audit(staff.toString(),"affiliate.paid",ids.toString(),reason+" · "+reference);});return Map.of("recorded",ids.size());}
  public Map<String,Object> content(){return data.content();}
  public Map<String,Object> publish(Map<String,Object> body,UUID staff){String reason=reason(body);String kind=Objects.toString(body.get("kind"),""),slug=Objects.toString(body.get("slug"),""),locale=Objects.toString(body.get("locale"),""),title=Objects.toString(body.get("title"),""),text=Objects.toString(body.get("body"),""),status=Objects.toString(body.get("status"),"");
    if(!Set.of("blog","roadmap","announcement").contains(kind)||!slug.matches("[a-z0-9][a-z0-9-]{0,79}")||!Set.of("fr","en","es","pt").contains(locale)||title.isBlank()||title.length()>200||text.length()>50000||!Set.of("published","withdrawn").contains(status)||!(body.get("version") instanceof Number n)||n.intValue()<1)throw new DomainException("invalid_request");
    tx.run(()->{data.publish(staff,body,clock.instant());audit.audit(staff.toString(),"content."+status,kind+"/"+slug+"/"+locale,reason);});return Map.of("published",true);
  }
  static UUID uuid(Object v){try{return UUID.fromString(Objects.toString(v));}catch(Exception e){throw new DomainException("invalid_request");}}
  static String reason(Map<String,Object> b){String s=Objects.toString(b.get("reason"),"").strip();if(s.length()<3||s.length()>1000)throw new DomainException("invalid_reason");return s;}
}
