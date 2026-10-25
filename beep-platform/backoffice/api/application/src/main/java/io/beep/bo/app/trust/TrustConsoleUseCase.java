package io.beep.bo.app.trust;
import io.beep.bo.app.port.*;
import io.beep.bo.app.auth.StepUp;
import io.beep.bo.domain.staff.*;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.Clock;
import java.util.*;

public class TrustConsoleUseCase {
  private static String reason(String value) {if(value==null||value.strip().length()<3||value.length()>1000)throw new io.beep.bo.domain.DomainException("invalid_reason");return value.strip();}
  private final CoreGatewayPort core;private final AuditPort audit;private final StepUp stepUp;private final Clock clock;
  public TrustConsoleUseCase(CoreGatewayPort core,AuditPort audit,StepUp stepUp,Clock clock) {this.core=core;this.audit=audit;this.stepUp=stepUp;this.clock=clock;}
  private void log(StaffUser actor,String action,String target,String reason,String ip) {audit.append(new AuditEvent(clock.instant(),actor.id(),actor.email().value(),action,target,reason,ip));}
  public CoreGatewayPort.Reports reports(StaffUser actor,String status,int limit,int offset) {actor.require(Permission.LINKS_MODERATE);return core.reports(status,limit,offset,actor.id());}
  public CoreGatewayPort.AbuseReport resolve(StaffUser actor,UUID id,String decision,String reason,String totp,String ip) {actor.require(Permission.LINKS_MODERATE);reason=reason(reason);if(!Set.of("blocked","dismissed").contains(decision==null?"":decision))throw new io.beep.bo.domain.DomainException("invalid_request");stepUp.require(actor,totp);var out=core.resolveReport(id,decision,reason,actor.id());log(actor,"moderation."+decision,id.toString(),reason,ip);return out;}
  public CoreGatewayPort.BlockedHosts blocks(StaffUser actor) {actor.require(Permission.LINKS_MODERATE);return core.blockedHosts(actor.id());}
  public CoreGatewayPort.BlockedHosts block(StaffUser actor,String host,boolean blocked,String reason,String totp,String ip) {actor.require(Permission.LINKS_MODERATE);reason=reason(reason);stepUp.require(actor,totp);var out=core.blockHost(host,blocked,reason,actor.id());log(actor,blocked?"host.block":"host.unblock",host,reason,ip);return out;}
  public CoreGatewayPort.FeatureFlags flags(StaffUser actor) {actor.require(Permission.FLAGS_READ);return core.featureFlags(actor.id());}
  public CoreGatewayPort.FeatureFlag flag(StaffUser actor,String key,boolean enabled,int rollout,int version,String reason,String totp,String ip) {actor.require(Permission.FLAGS_WRITE);reason=reason(reason);stepUp.require(actor,totp);var out=core.publishFlag(key,enabled,rollout,version,reason,actor.id());log(actor,"flag.publish",key,reason,ip);return out;}
  public Map<String,Object> export(StaffUser actor,UUID id,String totp,String ip) {actor.require(Permission.PRIVACY_EXPORT);stepUp.require(actor,totp);var out=core.exportUser(id,actor.id());log(actor,"privacy.export",id.toString(),null,ip);return out;}
  public void erase(StaffUser actor,UUID id,String reason,String totp,String ip) {actor.require(Permission.PRIVACY_ERASE);reason=reason(reason);stepUp.require(actor,totp);core.eraseUser(id,reason,actor.id());log(actor,"privacy.erase",id.toString(),reason,ip);}
}
