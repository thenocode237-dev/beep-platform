package io.beep.core.adapter.in.http;
import io.beep.core.app.conversion.ClickIds;
import io.beep.core.app.port.DeveloperRepository;
import io.beep.core.domain.DomainException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.net.URI;

@RestController @Profile("!worker & !migrate")
public class TrackingAssetsController {
  private static final String STYLE="<style>body{font:16px/1.6 system-ui,sans-serif;margin:0}main{max-width:34rem;margin:5vh auto;padding:24px}a,button{box-sizing:border-box;display:flex;align-items:center;justify-content:center;min-height:48px;padding:12px 16px;font:inherit;overflow-wrap:anywhere}button{width:100%;cursor:pointer}form{margin-top:16px}h1{font-size:1.7rem;line-height:1.2}</style>";
  private final ClickIds ids;private final DeveloperRepository data;private final Clock clock;private final io.beep.core.app.conversion.DeveloperUseCase developer;private final String origin;
  public TrackingAssetsController(ClickIds ids,DeveloperRepository data,Clock clock,io.beep.core.app.conversion.DeveloperUseCase developer,io.beep.core.app.common.PublicSite site){this.ids=ids;this.data=data;this.clock=clock;this.developer=developer;URI u=URI.create(site.url());int port=u.getPort();this.origin=u.getScheme()+"://"+u.getHost()+(port<0||port==443&&u.getScheme().equals("https")||port==80&&u.getScheme().equals("http")?"":":"+port);}
  @GetMapping(value="/beep.js",produces="application/javascript") ResponseEntity<byte[]> script()throws Exception {try(var s=getClass().getResourceAsStream("/beep.js")){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).header("Access-Control-Allow-Origin","*").body(s.readAllBytes());}}
  @GetMapping(value="/v1/openapi.json",produces="application/json") ResponseEntity<byte[]> spec()throws Exception{try(var s=getClass().getResourceAsStream("/beep-public.openapi.yaml")){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(s.readAllBytes());}}
  private String target(String cid,String to,String sig){
    if(cid.length()>512||to.length()>12000||!MessageDigest.isEqual(ids.sign(cid+"."+to).getBytes(StandardCharsets.US_ASCII),sig.getBytes(StandardCharsets.US_ASCII)))throw new DomainException("invalid_request");
    try{String url=new String(Base64.getUrlDecoder().decode(to),StandardCharsets.UTF_8);var uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException();return url;}catch(RuntimeException e){throw new DomainException("invalid_request");}
  }
  private static String h(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
  @GetMapping("/_b/retarget") ResponseEntity<String> consent(@RequestParam String cid,@RequestParam String to,@RequestParam String sig){
    String url=target(cid,to,sig);String html="<!doctype html><html lang=fr><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Continuer avec BeepClick</title><main><h1>Votre choix pour la mesure publicitaire</h1><p>Ce lien propose des pixels Meta, Google ou TikTok pour mesurer la visite. Ils se chargent uniquement avec votre accord.</p><a href='"+h(url)+"'>Continuer sans accepter</a><form method=post><input type=hidden name=cid value='"+h(cid)+"'><input type=hidden name=to value='"+h(to)+"'><input type=hidden name=sig value='"+h(sig)+"'><button name=consent value=yes>Accepter et continuer</button></form></main></html>";
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'").header("Referrer-Policy","same-origin").body(html.replace("<main>",STYLE+"<main>"));
  }
  @PostMapping(value="/_b/retarget",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE) ResponseEntity<String> accept(@RequestParam String cid,@RequestParam String to,@RequestParam String sig,@RequestParam String consent,jakarta.servlet.http.HttpServletRequest request)throws Exception{
    if(!origin.equals(request.getHeader("Origin")))throw new DomainException("forbidden");
    String url=target(cid,to,sig);if(!"yes".equals(consent))throw new DomainException("consent_required");
    if("1".equals(request.getHeader("Sec-GPC")))return ResponseEntity.status(302).location(URI.create(url)).cacheControl(CacheControl.noStore()).build();
    // The workspace is part of the signed receipt, validated again before resolving configuration.
    String raw=new String(Base64.getUrlDecoder().decode(cid.split("\\.")[0]),StandardCharsets.UTF_8);UUID ws=UUID.fromString(raw.split("/")[0]);var click=ids.read(cid,ws,clock.instant()).orElse(null);
    DeveloperRepository.Tracking config;
    try{config=click==null||!developer.retargetingAllowed(ws)?null:data.tracking(click.link()).filter(c->c.enabled()&&c.workspaceId().equals(ws)).orElse(null);}catch(RuntimeException failure){config=null;}
    if(config==null)return ResponseEntity.status(302).location(URI.create(url)).cacheControl(CacheControl.noStore()).build();
    String nonce=UUID.randomUUID().toString();var js=new StringBuilder();var p=config.pixels();
    if(p.containsKey("meta"))js.append("!function(f,b,e,v,n,t,s){if(f.fbq)return;n=f.fbq=function(){n.callMethod?n.callMethod.apply(n,arguments):n.queue.push(arguments)};if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';n.queue=[];t=b.createElement(e);t.async=!0;t.src=v;s=b.getElementsByTagName(e)[0];s.parentNode.insertBefore(t,s)}(window,document,'script','https://connect.facebook.net/en_US/fbevents.js');fbq('consent','grant');fbq('init','").append(p.get("meta")).append("');fbq('track','PageView');");
    if(p.containsKey("google"))js.append("window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments)};gtag('consent','default',{ad_storage:'denied',analytics_storage:'denied',ad_user_data:'denied',ad_personalization:'denied'});gtag('consent','update',{ad_storage:'granted',analytics_storage:'granted',ad_user_data:'granted',ad_personalization:'granted'});gtag('js',new Date());gtag('config','").append(p.get("google")).append("');var g=document.createElement('script');g.async=true;g.src='https://www.googletagmanager.com/gtag/js?id=").append(p.get("google")).append("';document.head.appendChild(g);");
    if(p.containsKey("tiktok"))js.append("window.TiktokAnalyticsObject='ttq';var ttq=window.ttq=[];ttq.methods=['page','track','identify','instances','debug','on','off','once','ready','alias','group','enableCookie','disableCookie','holdConsent','revokeConsent','grantConsent'];ttq.setAndDefer=function(t,e){t[e]=function(){t.push([e].concat(Array.prototype.slice.call(arguments)))}};ttq.methods.forEach(function(m){ttq.setAndDefer(ttq,m)});ttq._i={};ttq._i['").append(p.get("tiktok")).append("']=[];ttq._i['").append(p.get("tiktok")).append("']._u='https://analytics.tiktok.com/i18n/pixel/events.js';ttq._t={};ttq._t['").append(p.get("tiktok")).append("']=+new Date;ttq._o={};ttq._o['").append(p.get("tiktok")).append("']={};ttq.grantConsent();var t=document.createElement('script');t.async=true;t.src='https://analytics.tiktok.com/i18n/pixel/events.js?sdkid=").append(p.get("tiktok")).append("&lib=ttq';document.head.appendChild(t);ttq.page();");
    if(p.containsKey("tiktok"))js.append("ttq.instance=function(id){var instance=ttq._i[id]||[];ttq.methods.forEach(function(method){ttq.setAndDefer(instance,method)});return instance;};");
    String safeUrl=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(url).replace("<","\\u003c");js.append("setTimeout(function(){location.replace(").append(safeUrl).append(")},1500);");
    String guardedJs="if(navigator.globalPrivacyControl===true){location.replace("+safeUrl+");}else{"+js+"}";
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; script-src 'nonce-"+nonce+"' https://connect.facebook.net https://www.googletagmanager.com https://www.googleadservices.com https://www.google.com https://analytics.tiktok.com; connect-src https://*.facebook.com https://*.google-analytics.com https://*.google.com https://www.google.fr https://www.google.cm https://www.google.ga https://www.googletagmanager.com https://www.googleadservices.com https://googleads.g.doubleclick.net https://ad.doubleclick.net https://pagead2.googlesyndication.com https://*.tiktok.com; img-src https://*.facebook.com https://*.google.com https://www.google.fr https://www.google.cm https://www.google.ga https://*.google-analytics.com https://www.googletagmanager.com https://www.googleadservices.com https://googleads.g.doubleclick.net https://pagead2.googlesyndication.com https://*.tiktok.com; frame-src https://*.google.com https://www.googletagmanager.com; frame-ancestors 'none'")
      .body("<!doctype html><html lang=fr><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Continuer</title>"+STYLE+"<main><p>Ouverture de votre destination… <a href='"+h(url)+"'>Continuer</a></p></main><script nonce='"+nonce+"'>"+guardedJs+"</script></html>");
  }
}
