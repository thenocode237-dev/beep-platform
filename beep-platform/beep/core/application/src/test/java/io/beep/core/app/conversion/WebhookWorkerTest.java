package io.beep.core.app.conversion;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.port.*;
import io.beep.core.app.port.DeveloperRepository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
class WebhookWorkerTest {
 static final Instant NOW=Instant.parse("2026-10-25T12:00:00Z");
 static final String SECRET="whsec_test-key-with-at-least-thirty-two-characters";
 final UUID ws=UUID.randomUUID(),endpoint=UUID.randomUUID(),id=UUID.randomUUID();
 final AtomicReference<Object[]> result=new AtomicReference<>();
 void run(int attempt,int response){
  var delivery=new Delivery(id,ws,endpoint,"sale","{\"id\":\"event-1\"}\n",attempt,"sending",null,NOW,NOW);
  var data=(DeveloperRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DeveloperRepository.class},(proxy,method,args)->switch(method.getName()){case "claim"->List.of(delivery);case "endpoints"->List.of(new Endpoint(endpoint,ws,"https://example.com",Set.of("sale"),SECRET,true,NOW));case "delivered"->{result.set(args);yield null;}default->throw new UnsupportedOperationException(method.getName());});
  var cipher=new SecretCipherPort(){public String encrypt(String s){return s;}public String decrypt(String s){return s;}};
  var tx=new TransactionPort(){public <T>T run(java.util.function.Supplier<T> work){return work.get();}};
  var developer=new DeveloperUseCase(null,null,null,null,null,null,null,null,null,null){@Override public boolean webhooksAllowed(UUID workspace){return true;}};
  WebhookTransportPort transport=(url,body,signature,deliveryId)->{String timestamp=Long.toString(NOW.getEpochSecond());assertThat(signature).isEqualTo("t="+timestamp+",v1="+new ClickIds(SECRET).sign(timestamp+"."+body));assertThat(body).endsWith("\n");assertThat(deliveryId).isEqualTo(id.toString());return response;};
  new WebhookWorker(data,cipher,transport,tx,Clock.fixed(NOW,ZoneOffset.UTC),developer).run();
 }
 @Test void signsExactBytesAndSchedulesExponentialBackoff(){run(1,503);assertThat(result.get()[3]).isEqualTo(NOW.plusSeconds(30));assertThat(result.get()[4]).isEqualTo(false);run(2,503);assertThat(result.get()[3]).isEqualTo(NOW.plusSeconds(60));}
 @Test void stopsAfterEightFailuresAndAcceptsOnly2xx(){run(8,302);assertThat(result.get()[4]).isEqualTo(true);run(1,204);assertThat(result.get()[2]).isEqualTo(204);}
}
