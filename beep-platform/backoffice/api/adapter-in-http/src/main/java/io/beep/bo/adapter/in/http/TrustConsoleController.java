package io.beep.bo.adapter.in.http;
import io.beep.bo.app.trust.TrustConsoleUseCase;
import io.beep.bo.app.port.CoreGatewayPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("!migrate")
@RequestMapping("/api")
public class TrustConsoleController {
  record Decision(String decision,String reason,String totp) {}
  record Block(boolean blocked,String reason,String totp) {}
  record Flag(boolean enabled,int rolloutPercent,int version,String reason,String totp) {}
  record Privacy(String reason,String totp) {}
  private final TrustConsoleUseCase trust;private final StaffSessions sessions;
  public TrustConsoleController(TrustConsoleUseCase trust,StaffSessions sessions) {this.trust=trust;this.sessions=sessions;}
  @GetMapping("/moderation/reports") CoreGatewayPort.Reports reports(@RequestParam(defaultValue="open") String status,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset,HttpServletRequest req) {return trust.reports(sessions.require(req),status,limit,offset);}
  @PostMapping("/moderation/reports/{id}/resolve") CoreGatewayPort.AbuseReport resolve(@PathVariable UUID id,@RequestBody Decision r,HttpServletRequest req) {return trust.resolve(sessions.require(req),id,r.decision(),r.reason(),r.totp(),req.getRemoteAddr());}
  @GetMapping("/moderation/blocklist") CoreGatewayPort.BlockedHosts blocks(HttpServletRequest req) {return trust.blocks(sessions.require(req));}
  @PutMapping("/moderation/blocklist/{host}") CoreGatewayPort.BlockedHosts block(@PathVariable String host,@RequestBody Block r,HttpServletRequest req) {return trust.block(sessions.require(req),host,r.blocked(),r.reason(),r.totp(),req.getRemoteAddr());}
  @GetMapping("/feature-flags") CoreGatewayPort.FeatureFlags flags(HttpServletRequest req) {return trust.flags(sessions.require(req));}
  @PutMapping("/feature-flags/{key}") CoreGatewayPort.FeatureFlag flag(@PathVariable String key,@RequestBody Flag r,HttpServletRequest req) {return trust.flag(sessions.require(req),key,r.enabled(),r.rolloutPercent(),r.version(),r.reason(),r.totp(),req.getRemoteAddr());}
  @PostMapping("/users/{id}/export") ResponseEntity<Map<String,Object>> export(@PathVariable UUID id,@RequestBody Privacy r,HttpServletRequest req) {return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=beep-account.json").body(trust.export(sessions.require(req),id,r.totp(),req.getRemoteAddr()));}
  @PostMapping("/users/{id}/erase") Map<String,Boolean> erase(@PathVariable UUID id,@RequestBody Privacy r,HttpServletRequest req) {trust.erase(sessions.require(req),id,r.reason(),r.totp(),req.getRemoteAddr());return Map.of("erased",true);}
}
