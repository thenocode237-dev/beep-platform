package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.JobRunRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresJobRunAdapter implements JobRunRepository {
  private final DSLContext dsl;

  public PostgresJobRunAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public long start(String job, Instant at) {
    return dsl.insertInto(JOB_RUNS).set(J_JOB, job).set(J_STARTED, odt(at)).returning(J_ID).fetchOne(J_ID);
  }

  @Override
  public void finish(long runId, Instant at, boolean ok, String detail) {
    dsl.update(JOB_RUNS).set(J_FINISHED, odt(at)).set(J_OK, ok).set(J_DETAIL, detail).where(J_ID.eq(runId)).execute();
  }

  @Override
  public List<JobSummary> summaries(Instant since) {
    Map<String, Instant> lastSuccess = new HashMap<>();
    dsl.fetch("SELECT job, max(finished_at) FROM job_runs WHERE ok GROUP BY job")
        .forEach(r -> lastSuccess.put(r.get(0, String.class), instant(r.get(1, OffsetDateTime.class))));
    Map<String, long[]> counts = new HashMap<>();
    dsl.fetch("SELECT job, count(*), count(*) FILTER (WHERE ok = false) FROM job_runs WHERE started_at >= ?::timestamptz GROUP BY job", odt(since))
        .forEach(r -> counts.put(r.get(0, String.class), new long[] {r.get(1, Long.class), r.get(2, Long.class)}));
    return dsl.fetch("SELECT DISTINCT ON (job) job, started_at, finished_at, ok, detail FROM job_runs ORDER BY job, started_at DESC").map(r -> {
      String job = r.get(0, String.class);
      long[] c = counts.getOrDefault(job, new long[2]);
      return new JobSummary(job, instant(r.get(1, OffsetDateTime.class)), instant(r.get(2, OffsetDateTime.class)), r.get(3, Boolean.class),
          r.get(4, String.class), lastSuccess.get(job), c[0], c[1]);
    });
  }

  @Override
  public Optional<Instant> lastSuccess(String job) {
    Record r = dsl.fetchOne("SELECT max(finished_at) FROM job_runs WHERE job = ? AND ok", job);
    return Optional.ofNullable(r == null ? null : instant(r.get(0, OffsetDateTime.class)));
  }

  @Override
  public int deleteBefore(Instant cutoff) {
    return dsl.deleteFrom(JOB_RUNS).where(J_STARTED.lt(odt(cutoff))).execute();
  }
}
