package io.beep.bo.bootstrap;
import io.beep.bo.app.ecosystem.ConsoleMaintenanceUseCase;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling @Profile("!migrate & !seed")
public class ConsoleMaintenanceJobs {
  private final ConsoleMaintenanceUseCase maintenance;
  public ConsoleMaintenanceJobs(ConsoleMaintenanceUseCase maintenance){this.maintenance=maintenance;}
  @Scheduled(initialDelayString="PT1M",fixedDelayString="PT1H") void cleanup(){try{org.slf4j.LoggerFactory.getLogger(getClass()).info("console privacy reconciliation: {}",maintenance.run());}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("console privacy reconciliation deferred",e);}}
}
