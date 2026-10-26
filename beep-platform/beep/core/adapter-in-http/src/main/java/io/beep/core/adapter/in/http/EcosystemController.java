package io.beep.core.adapter.in.http;

import io.beep.core.app.ecosystem.EcosystemUseCase;
import io.beep.core.app.port.*;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Email;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import java.time.*;
import java.util.*;

@RestController @Profile("!worker & !migrate")
public class EcosystemController {
  record Invitation(String email,String role){} record Member(String role){} record Accept(String token){} record Select(UUID id){}
  record Label(String kind,String name){} record Organization(UUID folder,List<UUID> tags){} record Csv(String csv){}
  record Integration(String provider,String name,String secret,String account,String url){}
  record Rule(String kind,long threshold,String channel,String destination,UUID link){} record Report(String frequency,boolean enabled){}
  record Affiliate(boolean terms,String code){}
  private final EcosystemUseCase service;private final SessionCookies cookies;private final EcosystemRepository data;private final SecretCipherPort cipher;private final RateLimiterPort rate;private final TokenPort tokens;private final Clock clock;private final String origin;
  public EcosystemController(EcosystemUseCase service,SessionCookies cookies,EcosystemRepository data,SecretCipherPort cipher,RateLimiterPort rate,TokenPort tokens,Clock clock,io.beep.core.app.common.PublicSite site){this.service=service;this.cookies=cookies;this.data=data;this.cipher=cipher;this.rate=rate;this.tokens=tokens;this.clock=clock;var u=java.net.URI.create(site.url());origin=u.getScheme()+"://"+u.getRawAuthority();}
  @GetMapping("/v1/workspaces") Object workspaces(HttpServletRequest r){return Map.of("items",service.workspaces(cookies.requireUser(r)));}
  @PostMapping("/v1/workspaces") Object createWorkspace(@RequestBody Label s,HttpServletRequest r){return Map.of("id",service.createWorkspace(cookies.requireUser(r),s.name()));}
  @PostMapping("/v1/workspaces/select") ResponseEntity<?> select(@RequestBody Select s,HttpServletRequest r){var u=cookies.requireUser(r);if(s.id()==null||data.role(s.id(),u.id()).isEmpty())throw new DomainException("forbidden");return ResponseEntity.ok().header("Set-Cookie",ResponseCookie.from("beep_workspace",s.id().toString()).httpOnly(true).secure(origin.startsWith("https:")).sameSite("Lax").path("/").maxAge(Duration.ofDays(30)).build().toString()).body(Map.of("selected",s.id()));}
  @GetMapping("/v1/team") Object team(HttpServletRequest r){return service.team(cookies.requireUser(r));}
  @PostMapping("/v1/team/invitations") Object invite(@RequestBody Invitation i,HttpServletRequest r){service.invite(cookies.requireUser(r),i.email(),i.role());return Map.of("invited",true);}
  @DeleteMapping("/v1/team/invitations/{id}") Object revoke(@PathVariable UUID id,HttpServletRequest r){service.revokeInvite(cookies.requireUser(r),id);return Map.of("revoked",true);}
  @PostMapping("/v1/team/accept") Object accept(@RequestBody Accept a,HttpServletRequest r){return Map.of("workspace",service.accept(cookies.requireUser(r),a.token()));}
  @PatchMapping("/v1/team/members/{id}") Object member(@PathVariable UUID id,@RequestBody Member m,HttpServletRequest r){service.member(cookies.requireUser(r),id,m.role());return Map.of("saved",true);}
  @DeleteMapping("/v1/team/members/{id}") Object remove(@PathVariable UUID id,HttpServletRequest r){service.member(cookies.requireUser(r),id,null);return Map.of("removed",true);}
  @GetMapping("/v1/team/audit") Object audit(HttpServletRequest r){return Map.of("items",service.audit(cookies.requireUser(r)));}
  @GetMapping("/v1/organization") Object organization(HttpServletRequest r){return service.organization(cookies.requireUser(r));}
  @PostMapping("/v1/organization/labels") Object label(@RequestBody Label l,HttpServletRequest r){return Map.of("id",service.label(cookies.requireUser(r),l.kind(),l.name()));}
  @DeleteMapping("/v1/organization/labels/{id}") Object deleteLabel(@PathVariable UUID id,HttpServletRequest r){service.removeLabel(cookies.requireUser(r),id);return Map.of("removed",true);}
  @PutMapping("/v1/organization/links/{id}") Object organize(@PathVariable UUID id,@RequestBody Organization o,HttpServletRequest r){service.organize(cookies.requireUser(r),id,o.folder(),o.tags());return Map.of("saved",true);}
  @PostMapping("/v1/organization/import") Object csv(@RequestBody Csv c,HttpServletRequest r){return Map.of("items",service.importCsv(cookies.requireUser(r),c.csv()));}
  @GetMapping("/v1/organization/export.csv") ResponseEntity<String> export(HttpServletRequest r){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header("Content-Disposition","attachment; filename=beep-links.csv").cacheControl(CacheControl.noStore()).body(service.exportCsv(cookies.requireUser(r)));}
  @GetMapping("/v1/integrations") Object integrations(HttpServletRequest r){return service.integrations(cookies.requireUser(r));}
  @PostMapping("/v1/integrations") Object integration(@RequestBody Integration i,HttpServletRequest r){return service.connect(cookies.requireUser(r),i.provider(),i.name(),i.secret(),i.account(),i.url());}
  @DeleteMapping("/v1/integrations/{id}") Object disconnect(@PathVariable UUID id,HttpServletRequest r){service.disconnect(cookies.requireUser(r),id);return Map.of("disconnected",true);}
  @PostMapping("/v1/integrations/events/{id}/replay") Object replay(@PathVariable UUID id,HttpServletRequest r){service.replay(cookies.requireUser(r),id);return Map.of("replayed",true);}
  @GetMapping("/v1/radar") Object radar(HttpServletRequest r){return service.radar(cookies.requireUser(r));}
  @PostMapping("/v1/radar/rules") Object rule(@RequestBody Rule x,HttpServletRequest r){return service.rule(cookies.requireUser(r),x.kind(),x.threshold(),x.channel(),x.destination(),x.link());}
  @DeleteMapping("/v1/radar/rules/{id}") Object removeRule(@PathVariable UUID id,HttpServletRequest r){service.removeRule(cookies.requireUser(r),id);return Map.of("removed",true);}
  @PutMapping("/v1/radar/reports") Object report(@RequestBody Report x,HttpServletRequest r){service.report(cookies.requireUser(r),x.frequency(),x.enabled());return Map.of("saved",true);}
  @GetMapping("/v1/insights") Object insights(HttpServletRequest r){return service.insights(cookies.requireUser(r));}
  @GetMapping("/v1/affiliates") Object affiliate(HttpServletRequest r){return service.affiliate(cookies.requireUser(r));}
  @PostMapping("/v1/affiliates/join") Object join(@RequestBody Affiliate a,HttpServletRequest r){return Map.of("code",service.joinAffiliate(cookies.requireUser(r),a.terms()));}
  @PostMapping("/v1/affiliates/referral") Object referral(@RequestBody Affiliate a,HttpServletRequest r){service.referral(cookies.requireUser(r),a.code());return Map.of("saved",true);}
  @GetMapping("/v1/bio-submissions") Object submissions(HttpServletRequest r){return Map.of("items",service.submissions(cookies.requireUser(r)));}
  @DeleteMapping("/v1/bio-submissions/{id}") Object eraseSubmission(@PathVariable UUID id,HttpServletRequest r){service.eraseSubmission(cookies.requireUser(r),id);return Map.of("erased",true);}
  @GetMapping("/v1/content") Object content(@RequestParam(defaultValue="blog") String kind,@RequestParam(defaultValue="fr") String locale,@RequestParam(required=false) String slug){if(!Set.of("blog","roadmap","announcement").contains(kind)||!Set.of("fr","en","es","pt").contains(locale)||slug!=null&&!slug.matches("[a-z0-9][a-z0-9-]{0,79}"))throw new DomainException("invalid_request");return data.publicContent(kind,locale,slug);}
  @GetMapping(value="/v1/radar/stream",produces="text/event-stream") ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> stream(HttpServletRequest request){
    var actor=cookies.requireUser(request);service.feature(actor,"features.insights");UUID workspace=service.workspace(actor);var emitter=new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(30000L);var active=new java.util.concurrent.atomic.AtomicBoolean(true);emitter.onCompletion(()->active.set(false));emitter.onTimeout(()->active.set(false));
    Thread.ofVirtual().start(()->{try(var scope=io.beep.core.app.workspace.WorkspaceScope.open(workspace)){long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);while(active.get()&&System.nanoTime()<until){var current=cookies.requireUser(request);service.feature(current,"features.insights");emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("radar").data(service.radar(current)));Thread.sleep(3000);}emitter.complete();}catch(Exception e){emitter.complete();}});return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(emitter);
  }
  @PostMapping(value="/_b/forms/{page}/{block}",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<String> submit(@PathVariable UUID page,@PathVariable String block,@RequestParam String email,@RequestParam(defaultValue="") String message,@RequestParam(defaultValue="") String website,@RequestParam(defaultValue="false") boolean consent,HttpServletRequest r){
    if(!origin.equals(r.getHeader("Origin"))||!consent)throw new DomainException("forbidden");if(!website.isBlank())return ResponseEntity.accepted().body("Merci.");
    if(email.length()>254||message.length()>2000||!block.matches("[A-Za-z0-9]{6,16}"))throw new DomainException("invalid_request");
    if(!rate.tryAcquire("bio-form:"+tokens.hash(r.getRemoteAddr()+":"+clock.instant().getEpochSecond()/3600),5,Duration.ofHours(1)))throw new DomainException("rate_limited");
    Email to=new Email(email);if(!data.submit(page,block,cipher.encrypt(to.value()),cipher.encrypt(message),clock.instant()))throw new DomainException("not_found");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'").body("<!doctype html><html lang=fr><meta name=viewport content='width=device-width,initial-scale=1'><title>Merci · Beep</title><main style='font:18px system-ui;margin:3rem auto;max-width:30rem;padding:1rem'><h1>Merci !</h1><p>Votre réponse a été enregistrée.</p></main></html>");
  }
}
