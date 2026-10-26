package io.beep.core.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.enterprise.EnterpriseUseCase;
import io.beep.core.app.auth.AuthOutcome;
import io.beep.core.app.port.RateLimiterPort;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class EnterpriseControllerTest {
 @Test void ssoKeepsTheSelectedWorkspaceAcrossTheTotpChallenge(){UUID id=UUID.randomUUID();var service=new EnterpriseUseCase(null,null,null,null,null,null,null,null,null,null,null,null,null){@Override public AuthOutcome complete(UUID w,String state,String browser,String code,String ip){return new AuthOutcome(null,null,"challenge");}};var rate=new RateLimiterPort(){public boolean tryAcquire(String key,int max,Duration window){return true;}public void reset(String key){}};var controller=new EnterpriseController(service,null,new HttpSettings(false,Duration.ofHours(1)),rate);var request=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){case "getCookies"->new Cookie[]{new Cookie("beep_sso","state")};case "getRemoteAddr"->"192.0.2.1";default->null;});var response=controller.callback(id,"state","code",request);assertThat(response.getStatusCode().value()).isEqualTo(302);assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/login/verify?token=challenge");assertThat(response.getHeaders().get("Set-Cookie")).anySatisfy(c->assertThat(c).contains("beep_workspace="+id));assertThat(response.getHeaders().get("Set-Cookie")).noneSatisfy(c->assertThat(c).contains("beep_session="));}
}
