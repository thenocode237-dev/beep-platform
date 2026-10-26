package io.beep.core.adapter.in.admin;
import io.beep.core.app.ecosystem.AdminEcosystemUseCase;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import java.util.*;

@RestController @Profile("!worker & !migrate") @RequestMapping("/internal/admin/v1")
public class AdminEcosystemController {
  private final AdminEcosystemUseCase service;
  public AdminEcosystemController(AdminEcosystemUseCase service){this.service=service;}
  @GetMapping("/ecosystem/monitor") Object monitor(){return service.monitor();}
  @GetMapping("/ecosystem/workspaces/{id}") Object workspace(@PathVariable UUID id){return service.workspace(id);}
  @PostMapping("/ecosystem/workspaces/{id}/actions") Object action(@PathVariable UUID id,@RequestBody Map<String,Object> body,@RequestHeader("X-Staff-Id") UUID staff){return service.action(id,body,staff);}
  @GetMapping("/ecosystem/affiliates") Object affiliates(){return service.affiliates();}
  @PostMapping("/ecosystem/affiliates/actions") Object payout(@RequestBody Map<String,Object> body,@RequestHeader("X-Staff-Id") UUID staff){return service.payout(body,staff);}
  @GetMapping("/content") Object content(){return service.content();}
  @PostMapping("/content/publications") Object publish(@RequestBody Map<String,Object> body,@RequestHeader("X-Staff-Id") UUID staff){return service.publish(body,staff);}
}
