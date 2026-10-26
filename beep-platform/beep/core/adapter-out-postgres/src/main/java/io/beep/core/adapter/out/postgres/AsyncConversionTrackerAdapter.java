package io.beep.core.adapter.out.postgres;
import io.beep.core.app.port.*;
import io.beep.core.app.port.DeveloperRepository.Tracking;
import io.beep.core.app.conversion.ClickIds;
import io.beep.core.app.analytics.Visit;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.analytics.UserAgent;
import com.github.benmanes.caffeine.cache.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;

/** Configuration cache and bounded, non-blocking redirect event sink. Failures never prevent navigation. */
public class AsyncConversionTrackerAdapter implements ConversionTrackerPort,AutoCloseable {
  private record Pending(UUID workspace,String cid,UUID link,Instant at) {}
  private final DeveloperRepository data;private final ClickIds ids;private final WorkspaceRepository repository;private final Workspaces workspaces;private final TransactionPort tx;private final String publicUrl;
  private final Cache<UUID,Optional<Tracking>> cache=Caffeine.newBuilder().maximumSize(200000).expireAfterWrite(Duration.ofSeconds(5)).build();
  private final ArrayBlockingQueue<Pending> queue=new ArrayBlockingQueue<>(20000);
  private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("conversion-writer").factory());
  public final AtomicLong dropped=new AtomicLong();
  public AsyncConversionTrackerAdapter(DeveloperRepository data,ClickIds ids,WorkspaceRepository repository,Workspaces workspaces,TransactionPort tx,String publicUrl){this.data=data;this.ids=ids;this.repository=repository;this.workspaces=workspaces;this.tx=tx;this.publicUrl=publicUrl.replaceAll("/+$","");writer.scheduleWithFixedDelay(this::flush,100,100,TimeUnit.MILLISECONDS);}
  public void invalidate(UUID id){cache.invalidate(id);}
  @Override public String track(UUID ws,UUID link,String url,Visit visit){
    if(!visit.countable()||UserAgent.parse(visit.userAgent()).bot())return url;
    try{var c=cache.get(link,k->data.tracking(k).filter(t->t.enabled()&&repository.findById(ws).filter(w->!w.suspended()&&workspaces.entitlements(w).enabled("features.conversion")).isPresent())).orElse(null);
      if(c==null)return url;
      String cid=ids.issue(ws,link,visit.at());if(!queue.offer(new Pending(ws,cid,link,visit.at()))){dropped.incrementAndGet();return url;}
      String target=withCid(url,cid);
      if(c.pixels().isEmpty())return target;
      String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(target.getBytes(StandardCharsets.UTF_8));
      return publicUrl+"/_b/retarget?cid="+cid+"&to="+encoded+"&sig="+ids.sign(cid+"."+encoded);
    }catch(RuntimeException failure){dropped.incrementAndGet();return url;}
  }
  /** Remove all incoming aliases, including percent-encoded names, before appending the signed receipt. */
  public static String withCid(String url,String cid){
    int hash=url.indexOf('#');String fragment=hash<0?"":url.substring(hash);String base=hash<0?url:url.substring(0,hash);
    int question=base.indexOf('?');
    if(question>=0){String query=Arrays.stream(base.substring(question+1).split("&",-1)).filter(pair->{String name=pair.split("=",2)[0];try{return !java.net.URLDecoder.decode(name,StandardCharsets.UTF_8).equals("beep_cid");}catch(IllegalArgumentException e){return true;}}).collect(java.util.stream.Collectors.joining("&"));base=base.substring(0,question)+(query.isEmpty()?"":"?"+query);}
    return base+(base.contains("?")?"&":"?")+"beep_cid="+cid+fragment;
  }
  private void flush(){var batch=new ArrayList<Pending>();queue.drainTo(batch,1000);if(batch.isEmpty())return;try{tx.run(()->{for(var p:batch)data.touch(p.workspace(),p.cid(),p.link(),null,p.at());});}catch(RuntimeException failure){dropped.addAndGet(batch.size());}}
  @Override public void close(){writer.shutdown();try{writer.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}flush();}
  public int queued(){return queue.size();}
}
