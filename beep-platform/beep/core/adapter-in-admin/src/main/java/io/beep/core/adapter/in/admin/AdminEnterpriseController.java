package io.beep.core.adapter.in.admin;
import io.beep.core.app.enterprise.EnterpriseUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @Profile("!worker & !migrate") @RequestMapping("/internal/admin/v1/enterprise")
public class AdminEnterpriseController {
 private final EnterpriseUseCase service;public AdminEnterpriseController(EnterpriseUseCase service){this.service=service;}
 @GetMapping("/overview") Object overview(){return service.overview();}
 @GetMapping("/workspaces/{id}") Object workspace(@PathVariable UUID id){return service.workspace(id);}
 @PutMapping(value="/workspaces/{id}",consumes="application/json") Object save(@PathVariable UUID id,@RequestBody Map<String,Object>b,@RequestHeader("X-Staff-Id")UUID staff){return service.save(id,b,staff);}
 @PostMapping(value="/sdk-releases",consumes="application/json") Object sdk(@RequestBody Map<String,Object>b,@RequestHeader("X-Staff-Id")UUID staff){return service.sdk(b,staff);}
 @PostMapping(value="/evidence",consumes="application/json") Object evidence(@RequestBody Map<String,Object>b,@RequestHeader("X-Staff-Id")UUID staff){return service.evidence(b,staff);}
 @PostMapping(value="/access-reviews",consumes="application/json") Object review(@RequestBody Map<String,Object>b,@RequestHeader("X-Staff-Id")UUID staff){return service.review(b,staff);}
 @PostMapping(value="/failovers",consumes="application/json") Object failover(@RequestBody Map<String,Object>b,@RequestHeader("X-Staff-Id")UUID staff){return service.failover(b,staff);}
}
