package io.beep.core.adapter.in.http;
import io.beep.core.app.port.PlanRepository;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("!worker & !migrate")
public class PublicPlansController {
  record Plan(String code,String name,long priceXaf,long priceQuarterlyXaf,long priceAnnualXaf,boolean available,Map<String,Long> entitlements) {}
  record Plans(List<Plan> items) {}
  private final PlanRepository plans;
  public PublicPlansController(PlanRepository plans){this.plans=plans;}
  @GetMapping("/v1/public/plans") Plans list(){return new Plans(plans.findAll().stream().map(p -> new Plan(p.code(),p.name(),p.priceXaf(),p.priceQuarterlyXaf(),p.priceAnnualXaf(),p.publicPlan(),p.entitlements())).toList());}
}
