package io.beep.core.adapter.in.http;
import io.beep.core.app.trust.TrustUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("!worker & !migrate")
public class AbuseController {
  record Report(String url,String category,String detail) {}
  private final TrustUseCase trust;
  public AbuseController(TrustUseCase trust) {this.trust=trust;}
  @PostMapping(value="/v1/reports",consumes="application/json") ResponseEntity<Void> report(@RequestBody Report r,HttpServletRequest req) {trust.report(r.url(),r.category(),r.detail(),req.getRemoteAddr());return ResponseEntity.accepted().build();}
}
