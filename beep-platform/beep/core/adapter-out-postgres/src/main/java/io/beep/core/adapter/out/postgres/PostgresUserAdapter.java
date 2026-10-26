package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.UserStatus;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

public class PostgresUserAdapter implements UserRepository {
  private final DSLContext dsl;

  public PostgresUserAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<User> findById(UUID id) {
    return dsl.selectFrom(USERS).where(U_ID.eq(id)).fetchOptional(PostgresUserAdapter::map);
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return dsl.selectFrom(USERS).where(U_EMAIL.eq(email.value())).fetchOptional(PostgresUserAdapter::map);
  }

  @Override
  public void insert(User u) {
    try {
      dsl.insertInto(USERS)
          .set(U_ID, u.id())
          .set(U_EMAIL, u.email().value())
          .set(U_PASSWORD, u.passwordHash())
          .set(U_STATUS, u.status().db())
          .set(U_VERIFIED, odt(u.emailVerifiedAt()))
          .set(U_TOTP, u.totpSecretCipher())
          .set(U_TOTP_ON, u.totpEnabled())
          .set(U_LOCALE, u.locale())
          .set(U_CREATED, odt(u.createdAt()))
          .execute();
    } catch (RuntimeException e) {
      if (isUniqueViolation(e)) throw new DuplicateEmailException();
      throw e;
    }
  }

  @Override
  public void update(User u) {
    dsl.update(USERS)
        .set(U_PASSWORD, u.passwordHash())
        .set(U_STATUS, u.status().db())
        .set(U_VERIFIED, odt(u.emailVerifiedAt()))
        .set(U_TOTP, u.totpSecretCipher())
        .set(U_TOTP_ON, u.totpEnabled())
        .set(U_LOCALE, u.locale())
        .set(U_UPDATED, DSL.currentOffsetDateTime())
        .where(U_ID.eq(u.id()))
        .execute();
  }

  /** Pagination par clé (created_at, id) décroissante : stable même quand des inscriptions arrivent pendant la lecture. */
  @Override
  public Page page(int limit, String cursor, String query) {
    org.jooq.Condition where = DSL.trueCondition();
    if (query != null && !query.isBlank()) where = where.and(U_EMAIL.likeIgnoreCase(Sql.contains(query), '\\'));
    if (cursor != null && !cursor.isBlank()) {
      Sql.Cursor c = Sql.cursor(cursor);
      where = where.and(DSL.row(U_CREATED, U_ID).lt(DSL.row(odt(c.at()), c.id())));
    }
    List<User> rows = dsl.selectFrom(USERS).where(where).orderBy(U_CREATED.desc(), U_ID.desc()).limit(limit + 1).fetch(PostgresUserAdapter::map);
    if (rows.size() <= limit) return new Page(rows, null);
    List<User> items = rows.subList(0, limit);
    User last = items.get(limit - 1);
    return new Page(items, Sql.cursor(last.createdAt(), last.id()));
  }

  private static User map(Record r) {
    return new User(
        r.get(U_ID),
        new Email(r.get(U_EMAIL)),
        r.get(U_PASSWORD),
        UserStatus.fromDb(r.get(U_STATUS)),
        instant(r.get(U_VERIFIED)),
        r.get(U_TOTP),
        r.get(U_TOTP_ON),
        r.get(U_LOCALE),
        instant(r.get(U_CREATED)));
  }

  static boolean isUniqueViolation(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof SQLException s && "23505".equals(s.getSQLState())) return true;
    }
    return false;
  }
}
