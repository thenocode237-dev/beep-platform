package io.beep.core.app.conversion;

import io.beep.core.app.port.*;
import io.beep.core.app.port.DeveloperRepository.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.conversion.Attribution;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;

public class DeveloperUseCase {
  public record IssuedKey(UUID id,String token,String prefix) {}
  public record Principal(User user,UUID workspace,Set<String> scopes,boolean publicKey,List<String> origins) {}
  public record EventCommand(UUID eventId,String name,String visitor,String cid,BigDecimal value,String currency,String page,boolean consent,Map<String,String> utm) {
    public EventCommand(UUID eventId,String name,String visitor,String cid,BigDecimal value,String currency,String page,boolean consent){this(eventId,name,visitor,cid,value,currency,page,consent,Map.of());}
  }
  private static final Set<String> WEBHOOK_EVENTS=Set.of("click","lead","sale","signup","subscription","link.created","domain.status_changed");
  private final DeveloperRepository data;
  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final UserRepository users;
  private final LinkRepository links;
  private final TokenPort tokens;
  private final SecretCipherPort cipher;
  private final TransactionPort tx;
  private final ClickIds ids;
  private final Clock clock;
  public DeveloperUseCase(DeveloperRepository data,Workspaces workspaces,WorkspaceRepository workspaceRepository,UserRepository users,LinkRepository links,TokenPort tokens,SecretCipherPort cipher,TransactionPort tx,ClickIds ids,Clock clock){this.data=data;this.workspaces=workspaces;this.workspaceRepository=workspaceRepository;this.users=users;this.links=links;this.tokens=tokens;this.cipher=cipher;this.tx=tx;this.ids=ids;this.clock=clock;}
  public Workspace workspace(User user){var w=workspaces.ensureFor(user);w.requireWritable();return w;}
  public long limit(Workspace ws,String key,long fallback){return workspaces.entitlements(ws).value(key).orElse(fallback);}
  public void require(Workspace ws,String feature){ws.requireWritable();if(!workspaces.entitlements(ws).enabled(feature))throw new DomainException("feature_unavailable");}
  private Workspace workspace(UUID id){var ws=workspaceRepository.findById(id).orElseThrow(()->new DomainException("unauthorized"));ws.requireWritable();return ws;}
  public IssuedKey issue(User user,String name,Set<String> scopes,List<String> origins,boolean publicKey){
    var ws=workspace(user);
    if(publicKey)require(ws,"features.conversion");else if(limit(ws,"api.rpm",0)<=0)throw new DomainException("feature_unavailable");
    if(name==null||name.isBlank()||name.length()>80||scopes==null||scopes.isEmpty()||scopes.stream().anyMatch(Objects::isNull)||!Set.of("read","write","events").containsAll(scopes))throw new DomainException("invalid_request");
    if(publicKey && !scopes.equals(Set.of("events")))throw new DomainException("invalid_request");
    var allowed=origins==null?List.<String>of():origins.stream().map(DeveloperUseCase::origin).distinct().toList();
    if(allowed.size()>20 || publicKey&&allowed.isEmpty())throw new DomainException("invalid_request");
    String raw=(publicKey?"pk_":"sk_")+tokens.newToken();UUID id=UUID.randomUUID();String prefix=raw.substring(0,12);
    tx.run(()->{data.lock(ws.id());if(data.keys(ws.id()).stream().filter(k->k.revokedAt()==null).count()>=20)throw new DomainException("quota_exceeded");data.key(new ApiKey(id,ws.id(),name.strip(),prefix,tokens.hash(raw),scopes,allowed,publicKey,null,clock.instant()));});
    return new IssuedKey(id,raw,prefix);
  }
  public static String origin(String value){try{var u=URI.create(value);if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPath()!=null&&!u.getPath().isEmpty()||value.length()>255)throw new IllegalArgumentException();return u.getScheme()+"://"+u.getRawAuthority().toLowerCase(Locale.ROOT);}catch(RuntimeException e){throw new DomainException("invalid_request");}}
  public Principal authenticate(String raw,String scope,boolean browser,String origin){
    var k=data.keyByHash(tokens.hash(raw==null?"":raw)).orElseThrow(()->new DomainException("unauthorized"));
    if(k.publicKey()!=browser||!k.scopes().contains(scope))throw new DomainException("forbidden");
    if(browser && (origin==null||!k.origins().contains(origin(origin))))throw new DomainException("forbidden");
    var ws=workspace(k.workspaceId());
    var user=users.findById(ws.ownerId()).filter(User::canLogin).orElseThrow(()->new DomainException("unauthorized"));
    if(browser)require(ws,"features.conversion");else if(limit(ws,"api.rpm",0)<=0)throw new DomainException("feature_unavailable");
    long rpm=browser?Math.max(60,limit(ws,"collect.rpm",2000)):limit(ws,"api.rpm",0);
    if(!data.rate(ws.id(),browser?"collect":"api",rpm,clock.instant()))throw new DomainException("rate_limited");
    return new Principal(user,ws.id(),k.scopes(),k.publicKey(),k.origins());
  }
  public List<Map<String,Object>> keys(UUID ws){return data.keys(ws).stream().map(k->{Map<String,Object> v=new LinkedHashMap<>();v.put("id",k.id());v.put("name",k.name());v.put("prefix",k.prefix());v.put("scopes",k.scopes());v.put("origins",k.origins());v.put("publicKey",k.publicKey());v.put("revokedAt",k.revokedAt());v.put("createdAt",k.createdAt());return v;}).toList();}
  public void revoke(User user,UUID id){data.revoke(workspace(user).id(),id,clock.instant());}
  public void configure(User user,UUID id,boolean enabled,String model,int windowDays,Map<String,String> pixels){
    var ws=workspace(user);require(ws,"features.conversion");
    links.findById(id).filter(l->l.workspaceId().equals(ws.id())).orElseThrow(()->new DomainException("not_found"));
    Attribution.select(List.of(),model,windowDays,clock.instant());
    if(pixels!=null&&pixels.values().stream().anyMatch(Objects::isNull))throw new DomainException("invalid_request");
    var p=pixels==null?Map.<String,String>of():Map.copyOf(pixels);
    if(!p.isEmpty())require(ws,"features.retargeting");
    if(p.size()>3||!Set.of("meta","google","tiktok").containsAll(p.keySet())||p.values().stream().anyMatch(s->!s.matches("[A-Za-z0-9_-]{3,64}")))throw new DomainException("invalid_request");
    data.tracking(new Tracking(id,ws.id(),enabled,model,windowDays,p));
  }
  public boolean collect(UUID workspace,EventCommand cmd){
    var ws=workspace(workspace);require(ws,"features.conversion");Instant now=clock.instant();
    if(cmd.eventId()==null||cmd.name()==null||!cmd.name().matches("pageview|lead|sale|signup|subscription|custom:[a-zA-Z0-9_.-]{1,64}")||cmd.visitor()!=null&&cmd.visitor().length()>128)throw new DomainException("invalid_request");
    if(!cmd.consent()&&!cmd.name().equals("pageview"))throw new DomainException("consent_required");
    var value=cmd.value()==null?BigDecimal.ZERO:cmd.value();
    if(value.signum()<0||value.precision()-value.scale()>14||value.scale()>4||cmd.currency()!=null&&!cmd.currency().matches("[A-Z]{3}")||"sale".equals(cmd.name())&&cmd.currency()==null)throw new DomainException("invalid_request");
    String page=page(cmd.page());String visitor=cmd.consent()&&cmd.visitor()!=null?ids.sign("visitor:"+workspace+":"+cmd.visitor()):null;
    var utm=cmd.utm()==null?Map.<String,String>of():cmd.utm();if(utm.size()>5||!Set.of("source","medium","campaign","term","content").containsAll(utm.keySet())||utm.values().stream().anyMatch(v->v==null||v.length()>128))throw new DomainException("invalid_utm");
    var receipt=cmd.consent()?ids.read(cmd.cid(),workspace,now):Optional.<ClickIds.Click>empty();
    if(cmd.consent()&&cmd.cid()!=null&&receipt.isEmpty())throw new DomainException("invalid_click_id");
    return tx.run(()->{
      data.lock(workspace);
      UUID attributed=null;String cid=null;
      if(receipt.isPresent()){
        var click=receipt.get();var config=data.tracking(click.link()).filter(c->c.workspaceId().equals(workspace)&&c.enabled()).orElse(null);
        if(config!=null){data.touch(workspace,cmd.cid(),click.link(),null,click.at());if(visitor!=null)data.identify(workspace,cmd.cid(),visitor);
          var touches=visitor==null?List.of(new Attribution.Touch(cmd.cid(),click.link(),click.at())):data.touches(workspace,visitor,now.minus(Duration.ofDays(config.windowDays())));
          var chosen=Attribution.select(touches,config.model(),config.windowDays(),now);
          if(chosen.isPresent()){attributed=chosen.get().linkId();cid=chosen.get().cid();}
        }
      }
      return data.event(new Event(cmd.eventId(),workspace,cmd.name(),visitor,cid,attributed,value,cmd.currency(),page,now,Map.copyOf(utm)),limit(ws,"events.monthly_max",100000));
    });
  }
  private static String page(String input){if(input==null)return null;try{if(input.length()>2048)throw new IllegalArgumentException();var u=URI.create(input);if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null)throw new IllegalArgumentException();return u.getScheme()+"://"+u.getHost()+(u.getPort()<0?"":":"+u.getPort())+(u.getRawPath()==null?"/":u.getRawPath());}catch(RuntimeException e){throw new DomainException("invalid_request");}}
  public Map<String,Object> analytics(User user,int days){var ws=workspace(user);require(ws,"features.conversion");if(days<1||days>36500)throw new DomainException("invalid_request");return data.analytics(ws.id(),java.time.LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC)).minusDays(days-1L).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());}
  public Map<String,Object> overview(User user){var ws=workspace(user);return overview(ws.id());}
  public Map<String,Object> overview(UUID ws){if(workspaceRepository.findById(ws).isEmpty())throw new DomainException("not_found");Map<String,Object> out=new LinkedHashMap<>();out.put("keys",keys(ws));out.put("tracking",data.trackingFor(ws));out.put("webhooks",data.endpoints(ws).stream().map(e->Map.of("id",e.id(),"url",e.url(),"events",e.events(),"enabled",e.enabled(),"provider",e.provider())).toList());out.put("deliveries",data.deliveries(ws));return out;}
  public Map<String,Object> endpoint(User user,String url,Set<String> events){var ws=workspace(user);require(ws,"features.webhooks");
    try{var u=URI.create(url);if(url.length()>2048||!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1&&u.getPort()!=443)throw new IllegalArgumentException();}catch(RuntimeException e){throw new DomainException("invalid_destination");}
    if(events==null||events.isEmpty()||events.size()>20||events.stream().anyMatch(e->e==null||!WEBHOOK_EVENTS.contains(e)&&!e.matches("custom:[a-zA-Z0-9_.-]{1,64}")))throw new DomainException("invalid_request");
    String secret="whsec_"+tokens.newToken();var e=new Endpoint(UUID.randomUUID(),ws.id(),url,Set.copyOf(events),cipher.encrypt(secret),true,clock.instant());
    tx.run(()->{data.lock(ws.id());if(data.endpoints(ws.id()).stream().filter(Endpoint::enabled).count()>=10)throw new DomainException("quota_exceeded");data.endpoint(e);});
    return Map.of("id",e.id(),"secret",secret);
  }
  public void disable(User user,UUID id){data.disableEndpoint(workspace(user).id(),id);}
  public Map<String,Object> postback(User user,String provider,String account,String token,String version){
    var ws=workspace(user);require(ws,"features.retargeting");require(ws,"features.webhooks");
    if(provider==null||!Set.of("meta","google").contains(provider)||account==null||!account.matches(provider.equals("meta")?"[0-9]{5,30}":"G-[A-Z0-9]{4,30}")||token==null||token.isBlank()||token.length()>2048||token.contains("\n")||token.contains("\r")||provider.equals("meta")&&(version==null||!version.matches("v[0-9]{1,3}\\.[0-9]{1,2}")))throw new DomainException("invalid_request");
    String url=provider.equals("meta")?"https://graph.facebook.com/"+version+"/"+account+"/events":"https://www.google-analytics.com/mp/collect";
    var e=new Endpoint(UUID.randomUUID(),ws.id(),url,Set.of("lead","sale"),cipher.encrypt("whsec_"+tokens.newToken()),true,clock.instant(),provider,cipher.encrypt(account+"\n"+token+"\n"+Objects.toString(version,"")));
    tx.run(()->{data.lock(ws.id());if(data.endpoints(ws.id()).stream().filter(Endpoint::enabled).count()>=10)throw new DomainException("quota_exceeded");data.endpoint(e);});return Map.of("id",e.id(),"provider",provider);
  }
  public void replay(User user,UUID id){var ws=workspace(user);require(ws,"features.webhooks");data.replay(ws.id(),id,clock.instant());}
  public boolean webhooksAllowed(UUID id){try{var ws=workspace(id);require(ws,"features.webhooks");return true;}catch(DomainException e){return false;}}
  public boolean retargetingAllowed(UUID id){try{var ws=workspace(id);require(ws,"features.conversion");require(ws,"features.retargeting");return true;}catch(DomainException e){return false;}}
  public List<Event> recent(User user,Instant after,UUID afterId){var ws=workspace(user);require(ws,"features.conversion");return data.events(ws.id(),after.isBefore(clock.instant().minus(Duration.ofDays(1)))?clock.instant().minus(Duration.ofDays(1)):after,afterId,100);}
}
