package io.beep.core.bootstrap;
import io.beep.core.app.conversion.WebhookWorker;
import io.beep.core.app.port.DeveloperRepository;
import io.beep.core.app.jobs.JobRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
@Configuration @Profile("worker")
public class Phase3Jobs {
  private final WebhookWorker worker;private final DeveloperRepository data;private final JobRunner jobs;private final Clock clock;
  public Phase3Jobs(WebhookWorker worker,DeveloperRepository data,JobRunner jobs,Clock clock){this.worker=worker;this.data=data;this.jobs=jobs;this.clock=clock;}
  @Scheduled(initialDelayString="PT10S",fixedDelayString="PT5S") void webhooks(){try{jobs.run("webhooks.deliver",worker::run);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).error("webhooks worker failed",e);}}
  @Scheduled(initialDelayString="PT5M",fixedDelayString="PT1H") void retention(){jobs.run("conversion.retention",()->{data.retention(clock.instant());return "purged";});}
}
