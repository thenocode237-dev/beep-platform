-- Phase 0 back office : comptes staff, sessions courtes, journal d'audit append-only (chaînage de hachage).

CREATE TABLE staff_users (
    id            uuid        PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    role          text        NOT NULL CHECK (role IN ('superadmin', 'support', 'finance', 'trust_safety', 'content', 'readonly')),
    totp_secret   text,                              -- chiffré (AES-GCM) ; TOTP obligatoire pour se connecter
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    CONSTRAINT staff_email_lowercase CHECK (email = lower(email))
);
CREATE UNIQUE INDEX staff_users_email_key ON staff_users (email);

CREATE TABLE staff_sessions (
    id           uuid        PRIMARY KEY,
    staff_id     uuid        NOT NULL REFERENCES staff_users (id) ON DELETE CASCADE,
    token_hash   text        NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL
);
CREATE INDEX staff_sessions_staff_idx ON staff_sessions (staff_id);

CREATE TABLE audit_log (
    seq         bigint      PRIMARY KEY,             -- attribué sous verrou : 1, 2, 3… sans trou
    at          timestamptz NOT NULL,
    actor_id    uuid,
    actor_email text,
    action      text        NOT NULL,
    target      text,
    detail      text,
    ip          text,
    prev_hash   text        NOT NULL,
    hash        text        NOT NULL UNIQUE
);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_id, at DESC);
CREATE INDEX audit_log_action_idx ON audit_log (action, at DESC);

-- Append-only : toute modification ou suppression est refusée au niveau de la base.
CREATE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log est en ajout seul (% interdit)', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER audit_log_no_update_delete BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_immutable();
