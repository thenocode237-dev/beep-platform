package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.PlatformMetricsUseCase;
import io.beep.core.app.jobs.JobCatalog;
import io.beep.core.app.port.PlatformMetricsRepository.DayCount;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exploitation : métriques plateforme v1, état des jobs. */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminOperationsController {
  record DayView(String day, long count) {}

  record Users(long total, long active, long pending, long suspended) {}

  record Activation(long signups, long activated, double rate) {}

  record Resources(long links, long qrCodes, long bioPages) {}

  record Database(long beepBytes) {}

  record Worker(Instant lastRollupAt, Long lagSeconds) {}

  record Events(long queued, long dropped, long written) {}

  record Metrics(Instant generatedAt, Users users, List<DayView> signups, Activation activation, List<DayView> clicks, Resources resources, Database database,
      Worker worker, Events events) {}

  record Job(String name, String description, String schedule, Instant lastStartedAt, Instant lastFinishedAt, Boolean lastOk, String lastDetail,
      Instant lastSuccessAt, long runs24h, long failures24h) {}

  record JobsList(List<Job> items) {}

  private final PlatformMetricsUseCase metrics;
  private final JobCatalog jobs;

  public AdminOperationsController(PlatformMetricsUseCase metrics, JobCatalog jobs) {
    this.metrics = metrics;
    this.jobs = jobs;
  }

  @GetMapping("/metrics")
  Metrics metrics(@RequestParam(defaultValue = "30") int days) {
    var m = metrics.execute(days);
    return new Metrics(m.generatedAt(), new Users(m.users().total(), m.users().active(), m.users().pending(), m.users().suspended()), days(m.signups()),
        new Activation(m.activation().signups(), m.activation().activated(), m.activationRate()), days(m.clicks()),
        new Resources(m.resources().links(), m.resources().qrCodes(), m.resources().bioPages()), new Database(m.databaseBytes()),
        new Worker(m.lastRollupAt(), m.lagSeconds()), new Events(m.events().queued(), m.events().dropped(), m.events().written()));
  }

  @GetMapping("/jobs")
  JobsList jobs() {
    return new JobsList(jobs.statuses().stream().map(s -> {
      var r = s.summary();
      return new Job(s.job().name(), s.job().description(), s.job().schedule(), r == null ? null : r.lastStartedAt(), r == null ? null : r.lastFinishedAt(),
          r == null ? null : r.lastOk(), r == null ? null : r.lastDetail(), r == null ? null : r.lastSuccessAt(), r == null ? 0 : r.runs(), r == null ? 0 : r.failures());
    }).toList());
  }

  private static List<DayView> days(List<DayCount> counts) {
    return counts.stream().map(c -> new DayView(c.day().toString(), c.count())).toList();
  }
}
