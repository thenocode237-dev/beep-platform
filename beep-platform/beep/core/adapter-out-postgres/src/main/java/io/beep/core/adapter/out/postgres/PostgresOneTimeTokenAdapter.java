package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.OneTimeTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

public class PostgresOneTimeTokenAdapter implements OneTimeTokenRepository {
  private final DSLContext dsl;

  public PostgresOneTimeTokenAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt) {
    dsl.insertInto(TOKENS).set(T_USER, userId).set(T_PURPOSE, purpose.name().toLowerCase()).set(T_HASH, tokenHash).set(T_EXPIRES, odt(expiresAt)).execute();
  }

  /** Une seule instruction : deux requêtes concurrentes ne peuvent jamais consommer le même jeton. */
  @Override
  public Optional<UUID> consume(Purpose purpose, String tokenHash, Instant now) {
    return dsl.update(TOKENS)
        .set(T_USED, odt(now))
        .where(T_PURPOSE.eq(purpose.name().toLowerCase()).and(T_HASH.eq(tokenHash)).and(T_USED.isNull()).and(T_EXPIRES.gt(odt(now))))
        .returning(T_USER)
        .fetchOptional()
        .map(r -> r.get(T_USER));
  }

  @Override
  public Optional<UUID> find(Purpose purpose, String tokenHash, Instant now) {
    return dsl.select(T_USER).from(TOKENS)
        .where(T_PURPOSE.eq(purpose.name().toLowerCase()).and(T_HASH.eq(tokenHash)).and(T_USED.isNull()).and(T_EXPIRES.gt(odt(now))))
        .fetchOptional(T_USER);
  }

  @Override
  public int deleteExpired(Instant now) {
    return dsl.deleteFrom(TOKENS).where(T_EXPIRES.le(odt(now))).execute();
  }
}
