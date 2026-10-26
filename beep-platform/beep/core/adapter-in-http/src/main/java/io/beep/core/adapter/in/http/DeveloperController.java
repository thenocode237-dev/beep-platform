package io.beep.core.adapter.in.http;
import io.beep.core.app.conversion.DeveloperUseCase;
import io.beep.core.app.conversion.DeveloperUseCase.EventCommand;
import io.beep.core.app.port.DeveloperRepository;
import io.beep.core.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.*;
import java.util.*;

@RestController @Profile("!worker & !migrate")
public class DeveloperController {
  record KeyRequest(String name,Set<String> scopes,List<String> origins,boolean publicKey) {}
  record TrackingRequest(boolean enabled,String model,int windowDays,Map<String,String> pixels) {}
  record EndpointRequest(String url,Set<String> events) {}
  record PostbackRequest(String provider,String account,String token,String version) {}
  record CollectRequest(String key,UUID eventId,String name,String visitor,String cid,java.math.BigDecimal value,String currency,String page,boolean consent,Map<String,String> utm) {}
  private final DeveloperUseCase developer;private final DeveloperRepository data;private final SessionCookies cookies;private final Clock clock;
  public DeveloperController(DeveloperUseCase developer,DeveloperRepository data,SessionCookies cookies,Clock clock){this.developer=developer;this.data=data;this.cookies=cookies;this.clock=clock;}
  @GetMapping("/v1/developer") Map<String,Object> overview(HttpServletRequest req){return developer.overview(cookies.requireUser(req));}
  @PostMapping("/v1/developer/keys") ResponseEntity<?> key(@RequestBody KeyRequest r,HttpServletRequest req){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(developer.issue(cookies.requireUser(req),r.name(),r.scopes(),r.origins(),r.publicKey()));}
  @DeleteMapping("/v1/developer/keys/{id}") Map<String,Boolean> revoke(@PathVariable UUID id,HttpServletRequest req){developer.revoke(cookies.requireUser(req),id);return Map.of("revoked",true);}
  @PutMapping("/v1/conversions/links/{id}") Map<String,Boolean> configure(@PathVariable UUID id,@RequestBody TrackingRequest r,HttpServletRequest req){developer.configure(cookies.requireUser(req),id,r.enabled(),r.model(),r.windowDays(),r.pixels());return Map.of("saved",true);}
  @PostMapping("/v1/events") ResponseEntity<?> event(@RequestBody EventCommand r,HttpServletRequest req){UUID ws=developer.workspace(cookies.requireUser(req)).id();return ResponseEntity.accepted().body(Map.of("accepted",developer.collect(ws,r)));}
  @GetMapping("/v1/conversions/analytics") Map<String,Object> analytics(@RequestParam(defaultValue="30") int days,HttpServletRequest req){return developer.analytics(cookies.requireUser(req),days);}
  @GetMapping(value="/v1/conversions/stream",produces="text/event-stream") ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> stream(@RequestParam(defaultValue="1970-01-01T00:00:00Z") Instant after,HttpServletRequest req){
    developer.recent(cookies.requireUser(req),after,null);
    UUID streamWorkspace=developer.workspace(cookies.requireUser(req)).id();
    var emitter=new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(30000L);var active=new java.util.concurrent.atomic.AtomicBoolean(true);emitter.onCompletion(()->active.set(false));emitter.onTimeout(()->active.set(false));
    Thread.ofVirtual().start(()->{Instant cursor=after;UUID last=null;
      try(var scope=io.beep.core.app.workspace.WorkspaceScope.open(streamWorkspace)){String header=req.getHeader("Last-Event-ID");if(header!=null){String[] p=header.split("\\|",-1);cursor=Instant.parse(p[0]);last=UUID.fromString(p[1]);}
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
        while(active.get()&&System.nanoTime()<deadline){var events=developer.recent(cookies.requireUser(req),cursor,last);for(var e:events){emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().id(e.at()+"|"+e.id()).name("conversion").data(e));cursor=e.at();last=e.id();}emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().comment("heartbeat"));Thread.sleep(1000);}emitter.complete();
      }catch(Exception e){emitter.complete();}
    });return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(emitter);
  }
  @PostMapping("/v1/webhooks") ResponseEntity<?> webhook(@RequestBody EndpointRequest r,HttpServletRequest req){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(developer.endpoint(cookies.requireUser(req),r.url(),r.events()));}
  @DeleteMapping("/v1/webhooks/{id}") Map<String,Boolean> disable(@PathVariable UUID id,HttpServletRequest req){developer.disable(cookies.requireUser(req),id);return Map.of("disabled",true);}
  @PostMapping("/v1/postbacks") ResponseEntity<?> postback(@RequestBody PostbackRequest r,HttpServletRequest req){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(developer.postback(cookies.requireUser(req),r.provider(),r.account(),r.token(),r.version()));}
  @PostMapping("/v1/webhooks/deliveries/{id}/replay") Map<String,Boolean> replay(@PathVariable UUID id,HttpServletRequest req){developer.replay(cookies.requireUser(req),id);return Map.of("replayed",true);}
  @PostMapping(value="/_b/collect",consumes={"application/json","text/plain"}) ResponseEntity<?> collect(HttpServletRequest req)throws Exception{
    byte[] bytes=req.getInputStream().readNBytes(16385);if(bytes.length>16384)throw new DomainException("invalid_request");String body=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
    try{var r=new com.fasterxml.jackson.databind.ObjectMapper().readValue(body,CollectRequest.class);if(r==null)throw new DomainException("invalid_request");var p=developer.authenticate(r.key(),"events",true,req.getHeader("Origin"));
      boolean bot=io.beep.core.domain.analytics.UserAgent.parse(req.getHeader("User-Agent")).bot();
      if(bot)data.rejection("bot",clock.instant());
      boolean accepted=!bot&&developer.collect(p.workspace(),new EventCommand(r.eventId(),r.name(),r.visitor(),r.cid(),r.value(),r.currency(),r.page(),r.consent(),r.utm()));
      return ResponseEntity.accepted().header("Access-Control-Allow-Origin",req.getHeader("Origin")).header("Vary","Origin").cacheControl(CacheControl.noStore()).body(Map.of("accepted",accepted));
    }catch(com.fasterxml.jackson.core.JsonProcessingException e){data.rejection("invalid_request",clock.instant());throw new DomainException("invalid_request");}catch(DomainException e){data.rejection(e.code(),clock.instant());throw e;}
  }
  @RequestMapping(value="/_b/collect",method=RequestMethod.OPTIONS) ResponseEntity<?> options(){return ResponseEntity.noContent().header("Access-Control-Allow-Methods","POST, OPTIONS").header("Access-Control-Allow-Headers","Content-Type").header("Access-Control-Max-Age","600").build();}
}
