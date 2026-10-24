package io.beep.bo.adapter.out.postgres;

import static io.beep.bo.adapter.out.postgres.Schema.*;

import io.beep.bo.app.port.StaffSessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;

public class PostgresStaffSessionAdapter implements StaffSessionRepository {
  private final DSLContext dsl;

  public PostgresStaffSessionAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Session s) {
    dsl.insertInto(SESSIONS).set(SS_ID, s.id()).set(SS_STAFF, s.staffId()).set(SS_HASH, s.tokenHash()).set(SS_CREATED, odt(s.createdAt()))
        .set(SS_SEEN, odt(s.lastSeenAt())).set(SS_EXPIRES, odt(s.expiresAt())).execute();
  }

  @Override
  public Optional<Session> findActive(String tokenHash, Instant now, Instant idleCutoff) {
    return dsl.selectFrom(SESSIONS)
        .where(SS_HASH.eq(tokenHash).and(SS_EXPIRES.gt(odt(now))).and(SS_SEEN.gt(odt(idleCutoff))))
        .fetchOptional(r -> new Session(r.get(SS_ID), r.get(SS_STAFF), r.get(SS_HASH), instant(r.get(SS_CREATED)), instant(r.get(SS_SEEN)), instant(r.get(SS_EXPIRES))));
  }

  @Override
  public void touch(String tokenHash, Instant now) {
    dsl.update(SESSIONS).set(SS_SEEN, odt(now)).where(SS_HASH.eq(tokenHash)).execute();
  }

  @Override
  public void delete(String tokenHash) {
    dsl.deleteFrom(SESSIONS).where(SS_HASH.eq(tokenHash)).execute();
  }

  @Override
  public int deleteExpired(Instant now, Instant idleCutoff) {
    return dsl.deleteFrom(SESSIONS).where(SS_EXPIRES.le(odt(now)).or(SS_SEEN.le(odt(idleCutoff)))).execute();
  }
}
