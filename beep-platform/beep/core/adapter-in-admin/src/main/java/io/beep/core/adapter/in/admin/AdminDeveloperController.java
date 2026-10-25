package io.beep.core.adapter.in.admin;
import io.beep.core.app.conversion.AdminDeveloperUseCase;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!worker & !migrate") @RequestMapping("/internal/admin/v1/developer")
public class AdminDeveloperController {
  private final AdminDeveloperUseCase developer;
  public AdminDeveloperController(AdminDeveloperUseCase developer){this.developer=developer;}
  @GetMapping("/monitor") Map<String,Object> monitor(){return developer.monitor();}
  @GetMapping("/workspaces/{workspaceId}") Map<String,Object> overview(@PathVariable UUID workspaceId){return developer.overview(workspaceId);}
  @PostMapping("/workspaces/{workspaceId}/actions") Map<String,Object> action(@PathVariable UUID workspaceId,@RequestBody AdminDeveloperUseCase.Action r,@RequestHeader("X-Staff-Id") String staff){return developer.action(workspaceId,r,staff);}
}
