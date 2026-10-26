package io.beep.core.adapter.in.http;
import io.beep.core.app.ecosystem.EcosystemUseCase;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import java.time.Duration;
import java.util.*;

@RestController @Profile("!worker & !migrate")
public class IntegrationHooksController {
  private final EcosystemUseCase service;private final RateLimiterPort rate;private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
  public IntegrationHooksController(EcosystemUseCase service,RateLimiterPort rate){this.service=service;this.rate=rate;}
  @PostMapping("/v1/integrations/hooks/{id}") ResponseEntity<?> hook(@PathVariable UUID id,HttpServletRequest r)throws Exception{
    if(!rate.tryAcquire("integration-hook:"+id,1000,Duration.ofMinutes(1)))throw new DomainException("rate_limited");
    byte[] body=r.getInputStream().readNBytes(262145);if(body.length>262144)throw new DomainException("invalid_request");
    String signature=r.getHeader("Stripe-Signature");if(signature==null)signature=r.getHeader("X-Shopify-Hmac-Sha256");if(signature==null)signature=r.getHeader("X-WC-Webhook-Signature");
    var i=service.verifyIncoming(id,body,signature,r.getHeader("X-Shopify-Shop-Domain"));
    try{var sale=CommercePayloads.normalize(i.provider(),json.readTree(body));boolean accepted=sale.isPresent()&&service.receive(i,sale.get().externalId(),sale.get().command());return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("accepted",accepted));}
    catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new DomainException("invalid_request");}
  }
}
