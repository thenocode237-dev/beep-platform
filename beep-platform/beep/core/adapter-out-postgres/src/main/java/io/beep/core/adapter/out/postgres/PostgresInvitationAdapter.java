package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.InvitationRepository;
import io.beep.core.domain.invitation.Invitation;
import io.beep.core.domain.user.Email;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

public class PostgresInvitationAdapter implements InvitationRepository {
  private final DSLContext dsl;

  public PostgresInvitationAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Une invitation non utilisée est renouvelée (nouvelle échéance) ; une invitation utilisée reste en l'état. */
  @Override
  public Invitation upsert(Invitation i) {
    dsl.execute("INSERT INTO beta_invitations (id, email, invited_by, created_at, expires_at) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz) "
            + "ON CONFLICT (email) DO UPDATE SET invited_by = EXCLUDED.invited_by, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at "
            + "WHERE beta_invitations.used_at IS NULL",
        i.id(), i.email().value(), i.invitedBy(), odt(i.createdAt()), odt(i.expiresAt()));
    return findByEmail(i.email()).orElseThrow();
  }

  @Override
  public Optional<Invitation> findByEmail(Email email) {
    return dsl.selectFrom(INVITATIONS).where(I_EMAIL.eq(email.value())).fetchOptional(PostgresInvitationAdapter::map);
  }

  @Override
  public boolean consume(Email email, Instant now) {
    return dsl.update(INVITATIONS).set(I_USED, odt(now))
        .where(I_EMAIL.eq(email.value()).and(I_USED.isNull()).and(I_EXPIRES.gt(odt(now)))).execute() == 1;
  }

  @Override
  public Page page(int limit, String cursor) {
    Condition where = DSL.trueCondition();
    if (cursor != null && !cursor.isBlank()) {
      Sql.Cursor c = Sql.cursor(cursor);
      where = DSL.row(I_CREATED, I_ID).lt(DSL.row(odt(c.at()), c.id()));
    }
    List<Invitation> rows = dsl.selectFrom(INVITATIONS).where(where).orderBy(I_CREATED.desc(), I_ID.desc()).limit(limit + 1).fetch(PostgresInvitationAdapter::map);
    if (rows.size() <= limit) return new Page(rows, null);
    List<Invitation> items = rows.subList(0, limit);
    Invitation last = items.get(limit - 1);
    return new Page(items, Sql.cursor(last.createdAt(), last.id()));
  }

  private static Invitation map(Record r) {
    return new Invitation(r.get(I_ID), new Email(r.get(I_EMAIL)), r.get(I_BY), instant(r.get(I_CREATED)), instant(r.get(I_EXPIRES)), instant(r.get(I_USED)));
  }
}
