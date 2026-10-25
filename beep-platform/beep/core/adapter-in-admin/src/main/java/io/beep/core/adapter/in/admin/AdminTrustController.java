package io.beep.core.adapter.in.admin;
import io.beep.core.app.trust.TrustUseCase;
import io.beep.core.app.privacy.PrivacyUseCase;
import io.beep.core.app.port.TrustRepository;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminTrustController {
  record Decision(String decision,String reason) {}
  record Block(boolean blocked,String reason) {}
  record FlagChange(boolean enabled,int rolloutPercent,int version,String reason) {}
  record Erasure(String reason) {}
  record Blocks(List<TrustRepository.BlockedHost> items) {}
  record Flags(List<TrustRepository.Flag> items) {}
  private final TrustUseCase trust;
  private final PrivacyUseCase privacy;
  public AdminTrustController(TrustUseCase trust,PrivacyUseCase privacy) {this.trust=trust;this.privacy=privacy;}
  @GetMapping("/moderation/reports") TrustRepository.Reports reports(@RequestParam(required=false) String status,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {return trust.reports(status,limit,offset);}
  @PostMapping("/moderation/reports/{id}/resolve") TrustRepository.Report resolve(@PathVariable UUID id,@RequestBody Decision r,@RequestHeader("X-Staff-Id") String staff) {return trust.resolve(id,r.decision(),r.reason(),AdminViews.staff(staff));}
  @GetMapping("/moderation/blocklist") Blocks blocks() {return new Blocks(trust.blocklist());}
  @PutMapping("/moderation/blocklist/{host}") Blocks block(@PathVariable String host,@RequestBody Block r,@RequestHeader("X-Staff-Id") String staff) {trust.block(host,r.blocked(),r.reason(),AdminViews.staff(staff));return blocks();}
  @GetMapping("/feature-flags") Flags flags() {return new Flags(trust.flags());}
  @PutMapping("/feature-flags/{key}") TrustRepository.Flag flag(@PathVariable String key,@RequestBody FlagChange r,@RequestHeader("X-Staff-Id") String staff) {return trust.flag(key,r.enabled(),r.rolloutPercent(),r.version(),r.reason(),AdminViews.staff(staff));}
  @PostMapping("/users/{id}/export") Map<String,Object> export(@PathVariable UUID id,@RequestHeader("X-Staff-Id") String staff) {return privacy.export(id,AdminViews.staff(staff));}
  @PostMapping("/users/{id}/erase") Map<String,Boolean> erase(@PathVariable UUID id,@RequestBody Erasure r,@RequestHeader("X-Staff-Id") String staff) {privacy.eraseStaff(id,r.reason(),AdminViews.staff(staff));return Map.of("erased",true);}
}
