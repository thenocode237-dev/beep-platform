package io.beep.core.bootstrap;
import io.beep.core.app.ecosystem.EcosystemWorker;
import io.beep.core.app.jobs.JobRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration @Profile("worker")
public class Phase4Jobs {
  private final EcosystemWorker worker;private final JobRunner jobs;
  public Phase4Jobs(EcosystemWorker worker,JobRunner jobs){this.worker=worker;this.jobs=jobs;}
  @Scheduled(initialDelayString="PT15S",fixedDelayString="PT5S") void incoming(){run("integrations.process",worker::incoming);}
  @Scheduled(initialDelayString="PT30S",fixedDelayString="PT1M") void health(){run("radar.health",worker::health);}
  @Scheduled(initialDelayString="PT1M",fixedDelayString="PT1M") void radar(){run("radar.evaluate",worker::radar);}
  @Scheduled(initialDelayString="PT1M",fixedDelayString="PT1M") void reports(){run("reports.schedule",worker::reports);}
  @Scheduled(initialDelayString="PT20S",fixedDelayString="PT10S") void notifications(){run("radar.notify",worker::notifications);}
  @Scheduled(initialDelayString="PT2M",fixedDelayString="PT1H") void affiliate(){run("affiliates.accrue",worker::affiliates);}
  @Scheduled(initialDelayString="PT5M",fixedDelayString="PT1H") void retention(){run("ecosystem.retention",worker::retention);}
  private void run(String name,java.util.function.Supplier<String> task){try{jobs.run(name,task);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).error("phase4 job failed: {}",name,e);}}
}
