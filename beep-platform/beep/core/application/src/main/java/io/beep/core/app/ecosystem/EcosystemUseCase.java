package io.beep.core.app.ecosystem;

import io.beep.core.app.port.*;
import io.beep.core.app.port.EcosystemRepository.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.conversion.DeveloperUseCase;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.team.WorkspaceRole;
import io.beep.core.domain.ecosystem.*;
import io.beep.core.domain.user.*;
import java.time.*;
import java.util.*;

/** Team, automation and growth rules. Provider I/O is deferred to workers. */
public class EcosystemUseCase {
  private final EcosystemRepository data;private final Workspaces workspaces;private final WorkspaceRepository ws;
  private final TransactionPort tx;private final TokenPort tokens;private final SecretCipherPort cipher;private final MailPort mail;
  private io.beep.core.app.conversion.DeveloperUseCase conversions;
  public EcosystemUseCase withConversions(io.beep.core.app.conversion.DeveloperUseCase c){this.conversions=c;return this;}
  private final DeveloperRepository developer;private final CreateLinkUseCase create;private final Clock clock;
  public EcosystemUseCase(EcosystemRepository data,Workspaces workspaces,WorkspaceRepository ws,TransactionPort tx,TokenPort tokens,SecretCipherPort cipher,MailPort mail,DeveloperRepository developer,CreateLinkUseCase create,Clock clock){this.data=data;this.workspaces=workspaces;this.ws=ws;this.tx=tx;this.tokens=tokens;this.cipher=cipher;this.mail=mail;this.developer=developer;this.create=create;this.clock=clock;}
  public UUID workspace(User u){return workspaces.ensureFor(u).id();}
  public UUID createWorkspace(User u,String name){if(!u.emailVerified()||name==null||name.isBlank()||name.length()>80)throw new DomainException("invalid_request");return tx.run(()->{UUID id=data.createWorkspace(u.id(),name.strip(),clock.instant());data.audit(id,u.id(),"workspace.created",id.toString(),clock.instant());return id;});}
  public WorkspaceRole role(User u){return data.role(workspace(u),u.id()).orElseThrow(()->new DomainException("forbidden"));}
  public void require(User u,boolean manage){var c=workspaces.context(u);c.workspace().requireWritable();var r=role(u);if(manage?!r.manages():!r.writes())throw new DomainException("forbidden");}
  public void feature(User u,String key){if(!workspaces.context(u).entitlements().enabled(key))throw new DomainException("feature_unavailable");}
  public List<Map<String,Object>> workspaces(User u){workspace(u);return data.workspaces(u.id());}
  public Map<String,Object> team(User u){var out=new LinkedHashMap<>(data.team(workspace(u)));out.put("role",role(u).db());out.put("workspaces",workspaces(u));out.put("limit",workspaces.context(u).entitlements().value("members.max").orElse(1));return out;}
  public void invite(User u,String email,String rawRole){
    require(u,true);feature(u,"features.teams");WorkspaceRole target=WorkspaceRole.parse(rawRole);role(u).requireManage(target);Email to=new Email(email);if(to.equals(u.email()))throw new DomainException("invalid_request");
    String token=tokens.newToken();UUID id=UUID.randomUUID(),wid=workspace(u);Instant now=clock.instant();
    tx.run(()->{ws.lock(wid);quotaMembers(u);data.invite(new Invite(id,wid,to.value(),target.db(),tokens.hash(token),now.plus(Duration.ofDays(7))));data.audit(wid,u.id(),"member.invited",id.toString(),now);});
    mail.sendWorkspaceMessage(to,u.locale(),"Invitation BeepClick",workspaces.ensureFor(u).name()+" · "+target.db(),"/app/team?invite="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));
  }
  private void quotaMembers(User u){var team=data.team(workspace(u));long used=((Number)team.get("seats")).longValue(),limit=workspaces.context(u).entitlements().value("members.max").orElse(1);if(limit>=0&&used>=limit)throw new DomainException("quota_reached");}
  public UUID accept(User u,String token){
    if(!u.emailVerified()||token==null||token.length()>512)throw new DomainException("forbidden");
    return tx.run(()->{var i=data.invitation(tokens.hash(token),clock.instant()).orElseThrow(()->new DomainException("invalid_token"));ws.lock(i.workspace());
      if(!u.email().value().equalsIgnoreCase(i.email()))throw new DomainException("forbidden");var w=ws.findById(i.workspace()).orElseThrow(()->new DomainException("not_found"));w.requireWritable();
      var e=workspaces.entitlements(w);if(!e.enabled("features.teams"))throw new DomainException("feature_unavailable");
      long active=((Number)data.team(i.workspace()).get("activeSeats")).longValue(),limit=e.value("members.max").orElse(1);if(data.role(i.workspace(),u.id()).isEmpty()&&limit>=0&&active>=limit)throw new DomainException("quota_reached");
      data.accept(i,u.id(),clock.instant());data.audit(i.workspace(),u.id(),"member.joined",i.id().toString(),clock.instant());return i.workspace();});
  }
  public void member(User u,UUID member,String rawRole){require(u,true);UUID wid=workspace(u);WorkspaceRole current=data.role(wid,member).orElseThrow(()->new DomainException("not_found"));role(u).requireManage(current);
    tx.run(()->{ws.lock(wid);if(rawRole==null)data.remove(wid,member);else{WorkspaceRole target=WorkspaceRole.parse(rawRole);role(u).requireManage(target);data.member(wid,member,target.db());}data.audit(wid,u.id(),rawRole==null?"member.removed":"member.role",member.toString(),clock.instant());});}
  public void revokeInvite(User u,UUID id){require(u,true);data.revokeInvitation(workspace(u),id);data.audit(workspace(u),u.id(),"invitation.revoked",id.toString(),clock.instant());}
  public List<Map<String,Object>> audit(User u){feature(u,"features.workspace_audit");return data.audit(workspace(u));}
  public Map<String,Object> organization(User u){return Map.of("labels",data.organization(workspace(u)),"links",data.exportLinks(workspace(u)));}
  public UUID label(User u,String kind,String name){require(u,false);if(!Set.of("tag","folder").contains(kind)||name==null||name.isBlank()||name.length()>80)throw new DomainException("invalid_request");return tx.run(()->{ws.lock(workspace(u));if(data.organization(workspace(u)).size()>=200)throw new DomainException("quota_reached");UUID id=data.label(workspace(u),kind,name.strip());data.audit(workspace(u),u.id(),"label.saved",id.toString(),clock.instant());return id;});}
  public void removeLabel(User u,UUID id){require(u,false);tx.run(()->{data.removeLabel(workspace(u),id);data.audit(workspace(u),u.id(),"label.removed",id.toString(),clock.instant());});}
  public void organize(User u,UUID link,UUID folder,List<UUID> tags){require(u,false);if(tags==null||tags.size()>20)throw new DomainException("invalid_request");tx.run(()->{data.organize(workspace(u),link,folder,tags);data.audit(workspace(u),u.id(),"link.organized",link.toString(),clock.instant());});}
  public List<Map<String,Object>> importCsv(User u,String csv){require(u,false);feature(u,"features.export_csv");var rows=LinkCsv.parse(csv);var header=rows.getFirst();var result=new ArrayList<Map<String,Object>>();for(int n=1;n<rows.size();n++){var row=rows.get(n);var values=new HashMap<String,String>();for(int j=0;j<header.size();j++)values.put(header.get(j),row.get(j));try{var link=create.execute(u,new CreateLinkUseCase.Command(values.get("destination"),values.get("slug"),values.get("title")));result.add(Map.of("row",n+1,"id",link.id(),"slug",link.slug().value(),"status","created"));}catch(DomainException e){result.add(Map.of("row",n+1,"status","error","code",e.code()));}}
    data.audit(workspace(u),u.id(),"links.import",Integer.toString(rows.size()-1),clock.instant());return result;}
  public String exportCsv(User u){feature(u,"features.export_csv");var csv=new StringBuilder("destination,slug,title\r\n");for(var l:data.exportLinks(workspace(u)))csv.append(LinkCsv.cell((String)l.get("destination"))).append(',').append(LinkCsv.cell((String)l.get("slug"))).append(',').append(LinkCsv.cell((String)l.get("title"))).append("\r\n");return csv.toString();}
  public Map<String,Object> integrations(User u){UUID wid=workspace(u);return Map.of("items",data.integrations(wid).stream().map(i->Map.of("id",i.id(),"provider",i.provider(),"name",i.name(),"config",i.config(),"status",i.status(),"webhookPath","/v1/integrations/hooks/"+i.id())).toList(),"events",data.staffWorkspace(wid).get("events"));}
  public Map<String,Object> connect(User u,String provider,String name,String secret,String account,String url){
    require(u,true);feature(u,"features.integrations");if(!Set.of("stripe","shopify","woocommerce","zapier","make","slack","ga4").contains(provider))throw new DomainException("invalid_request");
    var ctx=workspaces.context(u);if(!ctx.entitlements().enabled("features.insights")&&!Set.of("zapier","make").contains(provider))throw new DomainException("feature_unavailable");
    if(name==null||name.isBlank()||name.length()>80||secret!=null&&secret.length()>4096)throw new DomainException("invalid_request");
    var config=new HashMap<String,String>();UUID endpoint=null;String credentials=Objects.toString(secret,"");
    if(Set.of("stripe","shopify","woocommerce").contains(provider)){if(secret==null||secret.length()<16)throw new DomainException("invalid_request");if(provider.equals("shopify")&&(account==null||!account.matches("[a-z0-9][a-z0-9-]{0,62}\\.myshopify\\.com")))throw new DomainException("invalid_request");if(account!=null&&!account.isBlank())config.put("account",account);}
    else if(provider.equals("ga4")){if(account==null||!account.matches("G-[A-Z0-9]{4,30}")||secret==null||!secret.matches("[A-Za-z0-9_-]{8,256}"))throw new DomainException("invalid_request");config.put("account",account);endpoint=UUID.randomUUID();credentials=account+"\n"+secret;}
    else {safeUrl(url);if(provider.equals("slack")&&!java.net.URI.create(url).getHost().equals("hooks.slack.com"))throw new DomainException("invalid_request");endpoint=UUID.randomUUID();credentials=url;config.put("delivery","signed-webhook");}
    String signingSecret="whsec_"+tokens.newToken();
    UUID id=UUID.randomUUID(),end=endpoint;String encrypted=cipher.encrypt(credentials);UUID wid=workspace(u);return tx.run(()->{ws.lock(wid);if(data.integrations(wid).stream().filter(i->!i.status().equals("disabled")).count()>=25)throw new DomainException("quota_reached");
      if(end!=null){boolean ga=provider.equals("ga4"),slack=provider.equals("slack");developer.endpoint(new DeveloperRepository.Endpoint(end,wid,ga?"https://www.google-analytics.com/mp/collect":slack?"https://hooks.slack.com/":"https://integration.invalid/",ga?Set.of("lead","sale"):Set.of("lead","sale","link.created"),cipher.encrypt(signingSecret),true,clock.instant(),ga?"google":slack?"slack":"webhook",encrypted));}
      data.integration(new Integration(id,wid,provider,name.strip(),config,encrypted,end,"active"));data.audit(wid,u.id(),"integration.connected",id.toString(),clock.instant());return Set.of("zapier","make").contains(provider)?Map.<String,Object>of("id",id,"signingSecret",signingSecret):Map.<String,Object>of("id",id);});
  }
  public static void safeUrl(String raw){try{var u=java.net.URI.create(raw);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1&&u.getPort()!=443||raw.length()>2048)throw new IllegalArgumentException();}catch(Exception e){throw new DomainException("invalid_request");}}
  public void disconnect(User u,UUID id){require(u,true);tx.run(()->{data.disconnect(workspace(u),id);data.audit(workspace(u),u.id(),"integration.disconnected",id.toString(),clock.instant());});}
  public Integration verifyIncoming(UUID id,byte[] body,String signature,String shop){var i=data.integration(id).filter(x->x.status().equals("active")||x.status().equals("error")).orElseThrow(()->new DomainException("not_found"));var w=ws.findById(i.workspace()).orElseThrow(()->new DomainException("not_found"));if(!w.status().db().equals("active")||!workspaces.entitlements(w).enabled("features.integrations"))throw new DomainException("feature_unavailable");
    if(i.provider().equals("shopify")&&!Objects.equals(shop,i.config().get("account")))throw new DomainException("unauthorized");if(!IntegrationSignatures.verify(i.provider(),cipher.decrypt(i.credentials()),body,signature,clock.instant()))throw new DomainException("unauthorized");return i;}
  public boolean receive(Integration i,String external,DeveloperUseCase.EventCommand command){if(external==null||external.isBlank()||external.length()>200)throw new DomainException("invalid_request");var c=new DeveloperUseCase.EventCommand(command.eventId(),command.name(),conversions.pseudonym(i.workspace(),command.visitor()),command.cid(),command.value(),command.currency(),command.page(),command.consent(),command.utm());return tx.run(()->{ws.lock(i.workspace());return data.incoming(new Incoming(c.eventId(),i.id(),i.workspace(),external,c.name(),c,"pending",0,clock.instant()));});}
  public void replay(User u,UUID id){require(u,true);data.replayIncoming(workspace(u),id,clock.instant());data.audit(workspace(u),u.id(),"integration.replay",id.toString(),clock.instant());}
  public Map<String,Object> radar(User u){feature(u,"features.radar");return data.radar(workspace(u));}
  public Map<String,Object> rule(User u,String kind,long threshold,String channel,String destination,UUID link){require(u,true);feature(u,"features.radar");if(!Set.of("click_spike","conversion_drop","broken_link","domain_error").contains(kind)||threshold<1||threshold>100000000||kind.equals("conversion_drop")&&threshold>100||!Set.of("email","slack","webhook").contains(channel))throw new DomainException("invalid_request");
    if(!channel.equals("email")){feature(u,"features.insights");safeUrl(destination);if(channel.equals("slack")&&!java.net.URI.create(destination).getHost().equals("hooks.slack.com"))throw new DomainException("invalid_request");}
    return tx.run(()->{ws.lock(workspace(u));if(((List<?>)data.radar(workspace(u)).get("rules")).size()>=50)throw new DomainException("quota_reached");Map<String,Object> hook=channel.equals("webhook")?conversions.endpoint(u,destination,Set.of("radar.alert")):Map.of();UUID endpoint=hook.isEmpty()?null:UUID.fromString(hook.get("id").toString());UUID id=data.rule(workspace(u),kind,threshold,channel,cipher.encrypt(channel.equals("email")?"owner":channel.equals("webhook")?"endpoint":destination),link,endpoint,clock.instant());data.audit(workspace(u),u.id(),"radar.rule",id.toString(),clock.instant());return hook.isEmpty()?Map.<String,Object>of("id",id):Map.<String,Object>of("id",id,"signingSecret",hook.get("secret"));});}
  public void removeRule(User u,UUID id){require(u,true);tx.run(()->{data.removeRule(workspace(u),id);data.audit(workspace(u),u.id(),"radar.removed",id.toString(),clock.instant());});}
  public void report(User u,String frequency,boolean enabled){require(u,true);feature(u,"features.reports");if(!Set.of("daily","weekly").contains(frequency))throw new DomainException("invalid_request");tx.run(()->{data.report(workspace(u),frequency,enabled,clock.instant());data.audit(workspace(u),u.id(),"report.configured",frequency,clock.instant());});}
  public Map<String,Object> insights(User u){feature(u,"features.insights");return data.insights(workspace(u),clock.instant().minus(Duration.ofDays(90)));}
  public Map<String,Object> affiliate(User u){return data.affiliate(u.id());}
  public String joinAffiliate(User u,boolean terms){if(!terms||!u.emailVerified())throw new DomainException("invalid_request");return data.joinAffiliate(u.id(),clock.instant());}
  public void referral(User u,String code){if(code==null||!code.matches("[a-z0-9]{12}"))throw new DomainException("invalid_request");if(role(u)!=WorkspaceRole.OWNER)throw new DomainException("forbidden");tx.run(()->{data.referral(u.id(),workspace(u),code,clock.instant());data.audit(workspace(u),u.id(),"referral.applied",code,clock.instant());});}
  public List<Map<String,Object>> submissions(User u){require(u,true);feature(u,"features.bio_advanced");return data.submissions(workspace(u)).stream().map(r->{var out=new LinkedHashMap<>(r);out.put("email",cipher.decrypt(Objects.toString(out.remove("email_cipher"))));out.put("message",cipher.decrypt(Objects.toString(out.remove("message_cipher"))));return (Map<String,Object>)out;}).toList();}
  public void eraseSubmission(User u,UUID id){require(u,true);tx.run(()->{data.eraseSubmission(workspace(u),id);data.audit(workspace(u),u.id(),"submission.erased",id.toString(),clock.instant());});}
}
