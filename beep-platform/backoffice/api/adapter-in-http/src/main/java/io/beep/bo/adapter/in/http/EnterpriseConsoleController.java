package io.beep.bo.adapter.in.http;
import io.beep.bo.app.enterprise.EnterpriseConsoleUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @Profile("!migrate") @RequestMapping("/api/enterprise")
public class EnterpriseConsoleController {
 private final EnterpriseConsoleUseCase service;private final StaffSessions sessions;public EnterpriseConsoleController(EnterpriseConsoleUseCase service,StaffSessions sessions){this.service=service;this.sessions=sessions;}
 @GetMapping("/overview") Object overview(HttpServletRequest r){return service.overview(sessions.require(r));}
 @GetMapping("/workspaces/{id}") Object workspace(@PathVariable UUID id,HttpServletRequest r){return service.workspace(sessions.require(r),id);}
 @PutMapping(value="/workspaces/{id}",consumes="application/json") Object save(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.action(sessions.require(r),"contract",id,b,r.getRemoteAddr());}
 @PostMapping(value="/{action:sdk-releases|evidence|access-reviews|staff-reviews|failovers}",consumes="application/json") Object action(@PathVariable String action,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.action(sessions.require(r),action,null,b,r.getRemoteAddr());}
}
