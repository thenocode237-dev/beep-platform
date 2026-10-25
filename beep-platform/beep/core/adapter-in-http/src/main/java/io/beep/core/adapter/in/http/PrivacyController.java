package io.beep.core.adapter.in.http;
import io.beep.core.app.privacy.PrivacyUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/account")
@Profile("!worker & !migrate")
public class PrivacyController {
  record Erasure(String token,String totp) {}
  private final PrivacyUseCase privacy;
  private final SessionCookies cookies;
  public PrivacyController(PrivacyUseCase privacy,SessionCookies cookies) {this.privacy=privacy;this.cookies=cookies;}
  @PostMapping(value="/export",consumes="application/json") ResponseEntity<Map<String,Object>> export(HttpServletRequest req) {
    var user=cookies.requireUser(req);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=beep-account.json").body(privacy.export(user.id(),user.id().toString()));
  }
  @PostMapping(value="/erase-request",consumes="application/json") ResponseEntity<Void> challenge(HttpServletRequest req) {privacy.challenge(cookies.requireUser(req));return ResponseEntity.accepted().build();}
  @PostMapping(value="/erase",consumes="application/json") ResponseEntity<Void> erase(@RequestBody Erasure r,HttpServletRequest req) {
    privacy.eraseSelf(cookies.requireUser(req),r.token(),r.totp());
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookies.clearSession().toString()).build();
  }
}
