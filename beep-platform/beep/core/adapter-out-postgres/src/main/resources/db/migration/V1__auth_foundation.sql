-- Phase 0 : comptes, sessions, jetons à usage unique.
-- Politique forward-only / expand-contract : on ne modifie jamais une migration appliquée.

CREATE TABLE users (
    id                uuid        PRIMARY KEY,
    email             text        NOT NULL,
    password_hash     text        NOT NULL,
    status            text        NOT NULL CHECK (status IN ('pending', 'active', 'suspended')),
    email_verified_at timestamptz,
    totp_secret       text,                          -- chiffré (AES-GCM), jamais en clair
    totp_enabled      boolean     NOT NULL DEFAULT false,
    locale            text        NOT NULL DEFAULT 'fr' CHECK (locale IN ('fr', 'en')),
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);
CREATE UNIQUE INDEX users_email_key ON users (email);
CREATE INDEX users_created_idx ON users (created_at DESC, id DESC);   -- pagination par curseur

CREATE TABLE sessions (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,          -- empreinte SHA-256 : le jeton brut n'est jamais stocké
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX sessions_user_idx ON sessions (user_id);
CREATE INDEX sessions_expires_idx ON sessions (expires_at);

CREATE TABLE one_time_tokens (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    text        NOT NULL CHECK (purpose IN ('verify_email', 'reset_password')),
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);
CREATE INDEX one_time_tokens_expires_idx ON one_time_tokens (expires_at);
