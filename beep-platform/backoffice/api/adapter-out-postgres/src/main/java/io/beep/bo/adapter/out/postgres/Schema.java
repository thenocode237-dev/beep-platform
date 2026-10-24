package io.beep.bo.adapter.out.postgres;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

/** Description typée du schéma sans génération de code : les migrations Flyway restent la source de vérité. */
final class Schema {
  private Schema() {}

  static final Table<?> STAFF = table(name("staff_users"));
  static final Field<UUID> ST_ID = field(name("staff_users", "id"), SQLDataType.UUID);
  static final Field<String> ST_EMAIL = field(name("staff_users", "email"), SQLDataType.CLOB);
  static final Field<String> ST_PASSWORD = field(name("staff_users", "password_hash"), SQLDataType.CLOB);
  static final Field<String> ST_ROLE = field(name("staff_users", "role"), SQLDataType.CLOB);
  static final Field<String> ST_TOTP = field(name("staff_users", "totp_secret"), SQLDataType.CLOB);
  static final Field<Boolean> ST_ACTIVE = field(name("staff_users", "active"), SQLDataType.BOOLEAN);
  static final Field<OffsetDateTime> ST_CREATED = field(name("staff_users", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> SESSIONS = table(name("staff_sessions"));
  static final Field<UUID> SS_ID = field(name("staff_sessions", "id"), SQLDataType.UUID);
  static final Field<UUID> SS_STAFF = field(name("staff_sessions", "staff_id"), SQLDataType.UUID);
  static final Field<String> SS_HASH = field(name("staff_sessions", "token_hash"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> SS_CREATED = field(name("staff_sessions", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> SS_SEEN = field(name("staff_sessions", "last_seen_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> SS_EXPIRES = field(name("staff_sessions", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> AUDIT = table(name("audit_log"));
  static final Field<Long> A_SEQ = field(name("audit_log", "seq"), SQLDataType.BIGINT);
  static final Field<OffsetDateTime> A_AT = field(name("audit_log", "at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<UUID> A_ACTOR = field(name("audit_log", "actor_id"), SQLDataType.UUID);
  static final Field<String> A_EMAIL = field(name("audit_log", "actor_email"), SQLDataType.CLOB);
  static final Field<String> A_ACTION = field(name("audit_log", "action"), SQLDataType.CLOB);
  static final Field<String> A_TARGET = field(name("audit_log", "target"), SQLDataType.CLOB);
  static final Field<String> A_DETAIL = field(name("audit_log", "detail"), SQLDataType.CLOB);
  static final Field<String> A_IP = field(name("audit_log", "ip"), SQLDataType.CLOB);
  static final Field<String> A_PREV = field(name("audit_log", "prev_hash"), SQLDataType.CLOB);
  static final Field<String> A_HASH = field(name("audit_log", "hash"), SQLDataType.CLOB);

  /** PostgreSQL stocke des microsecondes : on tronque pour relire exactement ce qu'on a écrit (indispensable au chaînage). */
  static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }

  static Instant instant(OffsetDateTime o) {
    return o == null ? null : o.toInstant();
  }
}
