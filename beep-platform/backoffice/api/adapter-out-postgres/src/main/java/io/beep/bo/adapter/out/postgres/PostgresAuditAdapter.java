package io.beep.bo.adapter.out.postgres;

import static io.beep.bo.adapter.out.postgres.Schema.*;

import io.beep.bo.app.port.AuditPort;
import io.beep.bo.domain.audit.AuditChain;
import io.beep.bo.domain.audit.AuditEvent;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * Journal append-only. L'ajout est sérialisé par un verrou consultatif transactionnel : la lecture de la dernière
 * empreinte et l'insertion forment une seule section critique, donc la chaîne reste linéaire même sous concurrence.
 */
public class PostgresAuditAdapter implements AuditPort {
  private static final long LOCK_KEY = 0x4245455041554449L; // « BEEPAUDI » : clé de verrou propre au journal

  private final DSLContext dsl;

  public PostgresAuditAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void append(AuditEvent raw) {
    AuditEvent event = new AuditEvent(raw.at().truncatedTo(ChronoUnit.MICROS), raw.actorId(), raw.actorEmail(), raw.action(), raw.target(), raw.detail(), raw.ip());
    dsl.transaction(cfg -> {
      DSLContext tx = cfg.dsl();
      tx.execute("SELECT pg_advisory_xact_lock(?)", LOCK_KEY);
      var last = tx.select(A_SEQ, A_HASH).from(AUDIT).orderBy(A_SEQ.desc()).limit(1).fetchOptional();
      long seq = last.map(r -> r.get(A_SEQ) + 1).orElse(1L);
      String prev = last.map(r -> r.get(A_HASH)).orElse(AuditChain.GENESIS);
      tx.insertInto(AUDIT)
          .set(A_SEQ, seq)
          .set(A_AT, odt(event.at()))
          .set(A_ACTOR, event.actorId())
          .set(A_EMAIL, event.actorEmail())
          .set(A_ACTION, event.action())
          .set(A_TARGET, event.target())
          .set(A_DETAIL, event.detail())
          .set(A_IP, event.ip())
          .set(A_PREV, prev)
          .set(A_HASH, AuditChain.hash(prev, event))
          .execute();
    });
  }

  @Override
  public List<AuditChain.Entry> readAll() {
    return dsl.selectFrom(AUDIT).orderBy(A_SEQ.asc()).fetch(PostgresAuditAdapter::map);
  }

  @Override
  public Page page(int limit, Long beforeSeq, String actionPrefix, String actorEmail) {
    org.jooq.Condition where = org.jooq.impl.DSL.trueCondition();
    if (beforeSeq != null) where = where.and(A_SEQ.lt(beforeSeq));
    if (actionPrefix != null) where = where.and(A_ACTION.startsWith(actionPrefix));
    if (actorEmail != null) where = where.and(A_EMAIL.equalIgnoreCase(actorEmail));
    List<AuditChain.Entry> rows = dsl.selectFrom(AUDIT).where(where).orderBy(A_SEQ.desc()).limit(limit + 1).fetch(PostgresAuditAdapter::map);
    if (rows.size() <= limit) return new Page(rows, null);
    List<AuditChain.Entry> items = rows.subList(0, limit);
    return new Page(items, items.get(limit - 1).seq());
  }

  private static AuditChain.Entry map(Record r) {
    AuditEvent e = new AuditEvent(instant(r.get(A_AT)), r.get(A_ACTOR), r.get(A_EMAIL), r.get(A_ACTION), r.get(A_TARGET), r.get(A_DETAIL), r.get(A_IP));
    return new AuditChain.Entry(r.get(A_SEQ), e, r.get(A_PREV), r.get(A_HASH));
  }
}
