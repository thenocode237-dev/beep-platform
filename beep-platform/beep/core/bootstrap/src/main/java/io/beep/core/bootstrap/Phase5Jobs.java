package io.beep.core.bootstrap;
import io.beep.core.app.port.EnterpriseRepository;
import io.beep.core.app.jobs.JobRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.*;
@Configuration @Profile("!migrate & !seed") @EnableScheduling
public class Phase5Jobs {
 private final EnterpriseRepository data;private final Clock clock;private final JobRunner jobs;private final String node,role,region;private final MeterRegistry metrics;
 public Phase5Jobs(EnterpriseRepository data,Clock clock,JobRunner jobs,MeterRegistry metrics,@Value("${BEEP_NODE_ID:${HOSTNAME:local}}")String node,@Value("${BEEP_SERVICE_ROLE:all}")String role,@Value("${BEEP_REGION:local}")String region){this.data=data;this.clock=clock;this.jobs=jobs;this.node=node;this.role=role;this.region=region;this.metrics=metrics;}
 @Scheduled(initialDelayString="PT2S",fixedDelayString="PT30S") void heartbeat(){try{Runtime r=Runtime.getRuntime();data.heartbeat(node,role,region,Map.of("processors",r.availableProcessors(),"heapUsed",r.totalMemory()-r.freeMemory(),"heapMax",r.maxMemory()),clock.instant());}catch(RuntimeException e){metrics.counter("beep.node.heartbeat.failures").increment();}}
 @Scheduled(initialDelayString="PT5M",fixedDelayString="PT15M") @Profile("worker") void retention(){if(!role.equals("worker"))return;jobs.run("enterprise.retention",()->{data.retention(clock.instant());return "purged";});}
}
