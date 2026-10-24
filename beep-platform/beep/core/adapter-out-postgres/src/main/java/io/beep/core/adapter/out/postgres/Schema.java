package io.beep.core.adapter.out.postgres;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

/** Description typée du schéma, sans génération de code : les migrations Flyway restent la seule source de vérité. */
final class Schema {
  private Schema() {}

  static final Table<?> USERS = table(name("users"));
  static final Field<UUID> U_ID = field(name("users", "id"), SQLDataType.UUID);
  static final Field<String> U_EMAIL = field(name("users", "email"), SQLDataType.CLOB);
  static final Field<String> U_PASSWORD = field(name("users", "password_hash"), SQLDataType.CLOB);
  static final Field<String> U_STATUS = field(name("users", "status"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> U_VERIFIED = field(name("users", "email_verified_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<String> U_TOTP = field(name("users", "totp_secret"), SQLDataType.CLOB);
  static final Field<Boolean> U_TOTP_ON = field(name("users", "totp_enabled"), SQLDataType.BOOLEAN);
  static final Field<String> U_LOCALE = field(name("users", "locale"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> U_CREATED = field(name("users", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> U_UPDATED = field(name("users", "updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> SESSIONS = table(name("sessions"));
  static final Field<UUID> S_ID = field(name("sessions", "id"), SQLDataType.UUID);
  static final Field<UUID> S_USER = field(name("sessions", "user_id"), SQLDataType.UUID);
  static final Field<String> S_HASH = field(name("sessions", "token_hash"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> S_CREATED = field(name("sessions", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> S_EXPIRES = field(name("sessions", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> TOKENS = table(name("one_time_tokens"));
  static final Field<UUID> T_USER = field(name("one_time_tokens", "user_id"), SQLDataType.UUID);
  static final Field<String> T_PURPOSE = field(name("one_time_tokens", "purpose"), SQLDataType.CLOB);
  static final Field<String> T_HASH = field(name("one_time_tokens", "token_hash"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> T_EXPIRES = field(name("one_time_tokens", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> T_USED = field(name("one_time_tokens", "used_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  // ---- phase 1 ----
  static final Table<?> PLANS = table(name("plans"));
  static final Field<String> P_CODE = field(name("plans", "code"), SQLDataType.CLOB);
  static final Field<String> P_NAME = field(name("plans", "name"), SQLDataType.CLOB);
  static final Field<Integer> P_VERSION = field(name("plans", "version"), SQLDataType.INTEGER);
  static final Field<Long> P_PRICE = field(name("plans", "price_xaf"), SQLDataType.BIGINT);
  static final Field<Boolean> P_PUBLIC = field(name("plans", "public"), SQLDataType.BOOLEAN);
  static final Field<JSONB> P_ENTITLEMENTS = field(name("plans", "entitlements"), SQLDataType.JSONB);

  static final Table<?> WORKSPACES = table(name("workspaces"));
  static final Field<UUID> W_ID = field(name("workspaces", "id"), SQLDataType.UUID);
  static final Field<UUID> W_OWNER = field(name("workspaces", "owner_id"), SQLDataType.UUID);
  static final Field<String> W_NAME = field(name("workspaces", "name"), SQLDataType.CLOB);
  static final Field<String> W_PLAN = field(name("workspaces", "plan_code"), SQLDataType.CLOB);
  static final Field<String> W_STATUS = field(name("workspaces", "status"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> W_FIRST_LINK = field(name("workspaces", "first_link_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> W_CREATED = field(name("workspaces", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> OVERRIDES = table(name("entitlement_overrides"));
  static final Field<UUID> O_WORKSPACE = field(name("entitlement_overrides", "workspace_id"), SQLDataType.UUID);
  static final Field<String> O_KEY = field(name("entitlement_overrides", "key"), SQLDataType.CLOB);
  static final Field<Long> O_VALUE = field(name("entitlement_overrides", "value"), SQLDataType.BIGINT);
  static final Field<String> O_REASON = field(name("entitlement_overrides", "reason"), SQLDataType.CLOB);
  static final Field<String> O_BY = field(name("entitlement_overrides", "updated_by"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> O_AT = field(name("entitlement_overrides", "updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> SLUGS = table(name("slugs"));
  static final Field<String> SL_SLUG = field(name("slugs", "slug"), SQLDataType.CLOB);
  static final Field<String> SL_KIND = field(name("slugs", "kind"), SQLDataType.CLOB);
  static final Field<UUID> SL_TARGET = field(name("slugs", "target_id"), SQLDataType.UUID);

  static final Table<?> LINKS = table(name("links"));
  static final Field<UUID> L_ID = field(name("links", "id"), SQLDataType.UUID);
  static final Field<UUID> L_WORKSPACE = field(name("links", "workspace_id"), SQLDataType.UUID);
  static final Field<String> L_SLUG = field(name("links", "slug"), SQLDataType.CLOB);
  static final Field<String> L_DESTINATION = field(name("links", "destination"), SQLDataType.CLOB);
  static final Field<String> L_TITLE = field(name("links", "title"), SQLDataType.CLOB);
  static final Field<String> L_STATUS = field(name("links", "status"), SQLDataType.CLOB);
  static final Field<String> L_REASON = field(name("links", "disabled_reason"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> L_CREATED = field(name("links", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> L_UPDATED = field(name("links", "updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> QR = table(name("qr_codes"));
  static final Field<UUID> Q_ID = field(name("qr_codes", "id"), SQLDataType.UUID);
  static final Field<UUID> Q_WORKSPACE = field(name("qr_codes", "workspace_id"), SQLDataType.UUID);
  static final Field<String> Q_KIND = field(name("qr_codes", "kind"), SQLDataType.CLOB);
  static final Field<String> Q_CODE = field(name("qr_codes", "code"), SQLDataType.CLOB);
  static final Field<UUID> Q_LINK = field(name("qr_codes", "link_id"), SQLDataType.UUID);
  static final Field<String> Q_CONTENT_TYPE = field(name("qr_codes", "content_type"), SQLDataType.CLOB);
  static final Field<JSONB> Q_CONTENT = field(name("qr_codes", "content"), SQLDataType.JSONB);
  static final Field<JSONB> Q_STYLE = field(name("qr_codes", "style"), SQLDataType.JSONB);
  static final Field<String> Q_TITLE = field(name("qr_codes", "title"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> Q_CREATED = field(name("qr_codes", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> Q_UPDATED = field(name("qr_codes", "updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> BIO = table(name("bio_pages"));
  static final Field<UUID> B_ID = field(name("bio_pages", "id"), SQLDataType.UUID);
  static final Field<UUID> B_WORKSPACE = field(name("bio_pages", "workspace_id"), SQLDataType.UUID);
  static final Field<String> B_SLUG = field(name("bio_pages", "slug"), SQLDataType.CLOB);
  static final Field<String> B_TITLE = field(name("bio_pages", "title"), SQLDataType.CLOB);
  static final Field<String> B_DESCRIPTION = field(name("bio_pages", "description"), SQLDataType.CLOB);
  static final Field<String> B_AVATAR = field(name("bio_pages", "avatar_url"), SQLDataType.CLOB);
  static final Field<String> B_THEME = field(name("bio_pages", "theme"), SQLDataType.CLOB);
  static final Field<JSONB> B_SEO = field(name("bio_pages", "seo"), SQLDataType.JSONB);
  static final Field<OffsetDateTime> B_CREATED = field(name("bio_pages", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> B_UPDATED = field(name("bio_pages", "updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> BLOCKS = table(name("bio_blocks"));
  static final Field<UUID> BB_PAGE = field(name("bio_blocks", "page_id"), SQLDataType.UUID);
  static final Field<String> BB_ID = field(name("bio_blocks", "id"), SQLDataType.CLOB);
  static final Field<Integer> BB_POSITION = field(name("bio_blocks", "position"), SQLDataType.INTEGER);
  static final Field<String> BB_TYPE = field(name("bio_blocks", "type"), SQLDataType.CLOB);
  static final Field<JSONB> BB_CONFIG = field(name("bio_blocks", "config"), SQLDataType.JSONB);

  static final Table<?> INVITATIONS = table(name("beta_invitations"));
  static final Field<UUID> I_ID = field(name("beta_invitations", "id"), SQLDataType.UUID);
  static final Field<String> I_EMAIL = field(name("beta_invitations", "email"), SQLDataType.CLOB);
  static final Field<String> I_BY = field(name("beta_invitations", "invited_by"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> I_CREATED = field(name("beta_invitations", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> I_EXPIRES = field(name("beta_invitations", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> I_USED = field(name("beta_invitations", "used_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

  static final Table<?> JOB_RUNS = table(name("job_runs"));
  static final Field<Long> J_ID = field(name("job_runs", "id"), SQLDataType.BIGINT);
  static final Field<String> J_JOB = field(name("job_runs", "job"), SQLDataType.CLOB);
  static final Field<OffsetDateTime> J_STARTED = field(name("job_runs", "started_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<OffsetDateTime> J_FINISHED = field(name("job_runs", "finished_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
  static final Field<Boolean> J_OK = field(name("job_runs", "ok"), SQLDataType.BOOLEAN);
  static final Field<String> J_DETAIL = field(name("job_runs", "detail"), SQLDataType.CLOB);

  /** PostgreSQL stocke des microsecondes : on tronque pour que ce qu'on écrit soit exactement ce qu'on relit. */
  static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }

  static Instant instant(OffsetDateTime o) {
    return o == null ? null : o.toInstant();
  }
}
