package io.beep.core.app.conversion;
import io.beep.core.app.port.*;
import java.time.*;
import java.util.concurrent.*;

public class WebhookWorker {
  private final DeveloperRepository data;private final SecretCipherPort cipher;private final WebhookTransportPort http;private final TransactionPort tx;private final Clock clock;private final DeveloperUseCase developer;
  public WebhookWorker(DeveloperRepository data,SecretCipherPort cipher,WebhookTransportPort http,TransactionPort tx,Clock clock,DeveloperUseCase developer){this.data=data;this.cipher=cipher;this.http=http;this.tx=tx;this.clock=clock;this.developer=developer;}
  public String run(){var due=tx.run(()->data.claim(clock.instant(),50));
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {var gate=new Semaphore(10);var tasks=due.stream().map(d->pool.submit(()->{
      try {gate.acquire();
        var endpoint=data.endpoints(d.workspaceId()).stream().filter(e->e.id().equals(d.endpointId())&&e.enabled()).findFirst().orElse(null);
        if(endpoint==null||!developer.endpointAllowed(endpoint)){data.delivered(d.id(),d.attempts(),403,clock.instant(),true,clock.instant());return;}
        Integer status=null;
        try{if(!endpoint.provider().equals("webhook"))status=http.postback(endpoint.provider(),cipher.decrypt(endpoint.configCipher()),d.payload(),d.id().toString());else{String ts=Long.toString(clock.instant().getEpochSecond());var signer=new ClickIds(cipher.decrypt(endpoint.secretCipher()));String signature="t="+ts+",v1="+signer.sign(ts+"."+d.payload());status=http.deliver(endpoint.configCipher()==null?endpoint.url():cipher.decrypt(endpoint.configCipher()),d.payload(),signature,d.id().toString());}}catch(Exception failure){status=null;}
        data.delivered(d.id(),d.attempts(),status,clock.instant().plusSeconds(Math.min(86400,30L*(1L<<Math.min(12,d.attempts()-1)))),d.attempts()>=8,clock.instant());
        developer.integrationDelivery(endpoint.id(),status);
      }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{gate.release();}
    })).toList();for(var t:tasks)try{t.get();}catch(Exception e){throw new IllegalStateException(e);}}
    return "deliveries="+due.size();
  }
}
