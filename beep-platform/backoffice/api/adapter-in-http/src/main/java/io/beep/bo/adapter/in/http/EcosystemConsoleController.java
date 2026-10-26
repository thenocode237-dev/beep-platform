package io.beep.bo.adapter.in.http;
import io.beep.bo.app.ecosystem.EcosystemConsoleUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController @Profile("!migrate") @RequestMapping("/api/ecosystem")
public class EcosystemConsoleController {
  private final EcosystemConsoleUseCase service;private final StaffSessions sessions;
  public EcosystemConsoleController(EcosystemConsoleUseCase service,StaffSessions sessions){this.service=service;this.sessions=sessions;}
  @GetMapping("/monitor") Object monitor(HttpServletRequest r){return service.monitor(sessions.require(r));}
  @GetMapping("/workspaces/{id}") Object workspace(@PathVariable UUID id,HttpServletRequest r){return service.workspace(sessions.require(r),id);}
  @PostMapping("/workspaces/{id}/actions") Object action(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r){var body=new LinkedHashMap<>(b);String totp=Objects.toString(body.remove("totp"),"");return service.action(sessions.require(r),id,body,totp,r.getRemoteAddr());}
  // Token stays in the request body, never URLs, Referer headers or HTTP access logs.
  @PostMapping("/impersonation/read") ResponseEntity<?> impersonation(@RequestBody Map<String,String> b,HttpServletRequest r){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.impersonation(sessions.require(r),Objects.toString(b.get("token"),"")));}
  @PostMapping("/impersonation/stop") Object stop(@RequestBody Map<String,String> b,HttpServletRequest r){service.stopImpersonation(sessions.require(r),Objects.toString(b.get("token"),""));return Map.of("stopped",true);}
  @GetMapping("/affiliates") Object affiliates(HttpServletRequest r){return service.affiliates(sessions.require(r));}
  @PostMapping("/affiliates/payouts") Object payouts(@RequestBody Map<String,Object> b,HttpServletRequest r){var body=new LinkedHashMap<>(b);String totp=Objects.toString(body.remove("totp"),"");return service.payout(sessions.require(r),body,totp,r.getRemoteAddr());}
  @GetMapping("/content") Object content(HttpServletRequest r){return service.drafts(sessions.require(r));}
  @PostMapping("/content") Object save(@RequestBody Map<String,Object> b,HttpServletRequest r){return service.save(sessions.require(r),b,r.getRemoteAddr());}
  @PostMapping("/content/{id}/publish") Object publish(@PathVariable UUID id,@RequestBody Map<String,String> b,HttpServletRequest r){return service.publish(sessions.require(r),id,b.getOrDefault("status","published"),b.getOrDefault("reason",""),b.getOrDefault("totp",""),r.getRemoteAddr());}
  @GetMapping("/staff") Object staff(HttpServletRequest r){return Map.of("items",service.staff(sessions.require(r)));}
  @PatchMapping("/staff/{id}") Object staffRole(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r){service.staffRole(sessions.require(r),id,Objects.toString(b.get("role"),""),Boolean.TRUE.equals(b.get("active")),Objects.toString(b.get("reason"),""),Objects.toString(b.get("totp"),""),r.getRemoteAddr());return Map.of("saved",true);}
}
