package io.beep.core.adapter.in.http;
import io.beep.core.app.enterprise.EnterpriseUseCase;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.domain.DomainException;
import jakarta.servlet.http.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!worker & !migrate")
public class EnterpriseController {
 private final EnterpriseUseCase service;private final SessionCookies cookies;private final HttpSettings settings;private final RateLimiterPort rate;
 public EnterpriseController(EnterpriseUseCase service,SessionCookies cookies,HttpSettings settings,RateLimiterPort rate){this.service=service;this.cookies=cookies;this.settings=settings;this.rate=rate;}
 @GetMapping("/v1/enterprise") Object settings(HttpServletRequest r){return service.settings(cookies.requireUser(r));}
 @PostMapping(value="/v1/enterprise/mobile-apps",consumes="application/json") Object app(@RequestBody Map<String,Object>b,HttpServletRequest r){return service.registerApp(cookies.requireUser(r),b);}
 @DeleteMapping("/v1/enterprise/mobile-apps/{id}") Object disable(@PathVariable UUID id,HttpServletRequest r){service.disableApp(cookies.requireUser(r),id);return Map.of("saved",true);}
 private void limit(HttpServletRequest r,String scope,int max){if(!rate.tryAcquire(scope+":"+r.getRemoteAddr(),max,Duration.ofMinutes(1)))throw new DomainException("rate_limited");}
 @PostMapping(value="/v1/mobile/deferred/{id}",consumes="application/json") ResponseEntity<?> issue(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){limit(r,"mobile.issue",30);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(service.issue(id,b));}
 @PostMapping(value="/v1/mobile/resolve",consumes="application/json") ResponseEntity<?> resolve(@RequestBody Map<String,Object>b,HttpServletRequest r){limit(r,"mobile.resolve",60);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resolve(b));}
 private ResponseCookie state(String value,Duration ttl){return ResponseCookie.from(settings.secureCookies()?"__Secure-beep_sso":"beep_sso",value).httpOnly(true).secure(settings.secureCookies()).sameSite("Lax").path("/v1/auth/sso").maxAge(ttl).build();}
 @GetMapping("/v1/auth/sso/{id}/start") ResponseEntity<Void> start(@PathVariable UUID id,HttpServletRequest r){limit(r,"sso.start",20);var out=service.start(id,r.getRemoteAddr());return ResponseEntity.status(302).location(URI.create(out.url())).header(HttpHeaders.SET_COOKIE,state(out.state(),Duration.ofMinutes(10)).toString()).cacheControl(CacheControl.noStore()).build();}
 @GetMapping("/v1/auth/sso/{id}/callback") ResponseEntity<Void> callback(@PathVariable UUID id,@RequestParam(required=false)String state,@RequestParam(required=false)String code,HttpServletRequest r){String saved=null;if(r.getCookies()!=null)for(Cookie c:r.getCookies())if(c.getName().equals(settings.secureCookies()?"__Secure-beep_sso":"beep_sso"))saved=c.getValue();var clear=state("",Duration.ZERO).toString();try{limit(r,"sso.callback",30);var out=service.complete(id,state,saved,code,r.getRemoteAddr());String path=out.mfaRequired()?"/login/verify?token="+URLEncoder.encode(out.mfaToken(),StandardCharsets.UTF_8):"/app";var response=ResponseEntity.status(302).location(URI.create(path)).header(HttpHeaders.SET_COOKIE,clear).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer");response.header(HttpHeaders.SET_COOKIE,ResponseCookie.from("beep_workspace",id.toString()).httpOnly(true).secure(settings.secureCookies()).sameSite("Lax").path("/").maxAge(settings.sessionTtl()).build().toString());if(!out.mfaRequired())response.header(HttpHeaders.SET_COOKIE,cookies.session(out.sessionToken(),settings.sessionTtl()).toString());return response.build();}catch(DomainException e){return ResponseEntity.status(302).location(URI.create("/login?error=sso_failed")).header(HttpHeaders.SET_COOKIE,clear).cacheControl(CacheControl.noStore()).build();}}
}
