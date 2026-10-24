package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.SessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

public class PostgresSessionAdapter implements SessionRepository {
  private final DSLContext dsl;

  public PostgresSessionAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Session s) {
    dsl.insertInto(SESSIONS).set(S_ID, s.id()).set(S_USER, s.userId()).set(S_HASH, s.tokenHash()).set(S_CREATED, odt(s.createdAt())).set(S_EXPIRES, odt(s.expiresAt())).execute();
  }

  @Override
  public Optional<Session> findActive(String tokenHash, Instant now) {
    return dsl.selectFrom(SESSIONS)
        .where(S_HASH.eq(tokenHash).and(S_EXPIRES.gt(odt(now))))
        .fetchOptional(r -> new Session(r.get(S_ID), r.get(S_USER), r.get(S_HASH), instant(r.get(S_CREATED)), instant(r.get(S_EXPIRES))));
  }

  @Override
  public void delete(String tokenHash) {
    dsl.deleteFrom(SESSIONS).where(S_HASH.eq(tokenHash)).execute();
  }

  @Override
  public void deleteAllForUser(UUID userId) {
    dsl.deleteFrom(SESSIONS).where(S_USER.eq(userId)).execute();
  }

  @Override
  public int deleteExpired(Instant now) {
    return dsl.deleteFrom(SESSIONS).where(S_EXPIRES.le(odt(now))).execute();
  }
}
