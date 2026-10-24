package io.beep.bo.adapter.out.postgres;

import static io.beep.bo.adapter.out.postgres.Schema.*;

import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresStaffAdapter implements StaffRepository {
  private final DSLContext dsl;

  public PostgresStaffAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<StaffUser> findById(UUID id) {
    return dsl.selectFrom(STAFF).where(ST_ID.eq(id)).fetchOptional(PostgresStaffAdapter::map);
  }

  @Override
  public Optional<StaffUser> findByEmail(Email email) {
    return dsl.selectFrom(STAFF).where(ST_EMAIL.eq(email.value())).fetchOptional(PostgresStaffAdapter::map);
  }

  @Override
  public void insert(StaffUser u) {
    dsl.insertInto(STAFF)
        .set(ST_ID, u.id())
        .set(ST_EMAIL, u.email().value())
        .set(ST_PASSWORD, u.passwordHash())
        .set(ST_ROLE, u.role().db())
        .set(ST_TOTP, u.totpSecretCipher())
        .set(ST_ACTIVE, u.active())
        .set(ST_CREATED, odt(u.createdAt()))
        .execute();
  }

  private static StaffUser map(Record r) {
    return new StaffUser(r.get(ST_ID), new Email(r.get(ST_EMAIL)), r.get(ST_PASSWORD), Role.fromDb(r.get(ST_ROLE)), r.get(ST_TOTP), r.get(ST_ACTIVE), instant(r.get(ST_CREATED)));
  }
}
