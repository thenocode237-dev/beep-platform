package io.beep.bo.adapter.in.http;
import io.beep.bo.app.developer.DeveloperConsoleUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @Profile("!migrate") @RequestMapping("/api/developer")
public class DeveloperConsoleController {
  private final DeveloperConsoleUseCase developer;private final StaffSessions sessions;
  public DeveloperConsoleController(DeveloperConsoleUseCase developer,StaffSessions sessions){this.developer=developer;this.sessions=sessions;}
  @GetMapping("/monitor") Map<String,Object> monitor(HttpServletRequest req){return developer.monitor(sessions.require(req));}
  @GetMapping("/workspaces/{id}") Map<String,Object> workspace(@PathVariable UUID id,HttpServletRequest req){return developer.workspace(sessions.require(req),id);}
  @PostMapping("/workspaces/{id}/actions") ResponseEntity<?> action(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest req){var payload=new HashMap<>(body);String totp=Objects.toString(payload.remove("totp"),"");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(developer.action(sessions.require(req),id,payload,totp,req.getRemoteAddr()));}
}
