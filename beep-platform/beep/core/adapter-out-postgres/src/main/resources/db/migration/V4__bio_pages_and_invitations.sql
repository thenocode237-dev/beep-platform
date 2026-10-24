-- Phase 1 · cycle 3 : pages link in bio, invitations à la bêta privée.

CREATE TABLE bio_pages (
    id           uuid        PRIMARY KEY,
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    slug         text        NOT NULL UNIQUE,                       -- inscrit aussi dans `slugs` (espace de noms partagé)
    title        text        NOT NULL,
    description  text,
    avatar_url   text,
    theme        text        NOT NULL DEFAULT 'ivoire',
    seo          jsonb       NOT NULL DEFAULT '{}',
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL
);
CREATE INDEX bio_pages_workspace_idx ON bio_pages (workspace_id, created_at);

CREATE TABLE bio_blocks (
    page_id  uuid    NOT NULL REFERENCES bio_pages (id) ON DELETE CASCADE,
    id       text    NOT NULL,
    position integer NOT NULL,
    type     text    NOT NULL CHECK (type IN ('link', 'button', 'heading', 'text', 'image', 'social')),
    config   jsonb   NOT NULL,
    PRIMARY KEY (page_id, id)
);
CREATE INDEX bio_blocks_order_idx ON bio_blocks (page_id, position);

CREATE FUNCTION beep_cache_notify_bio_pages() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify('beep_cache', 'slug:' || OLD.slug);
    PERFORM pg_notify('beep_cache', 'bio:' || OLD.id);
    RETURN NULL;
END;
$$;
CREATE TRIGGER bio_pages_cache AFTER UPDATE OR DELETE ON bio_pages
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_bio_pages();

CREATE FUNCTION beep_cache_notify_bio_blocks() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- notifications identiques dédoublonnées par PostgreSQL au sein d'une transaction : une seule par page remplacée
    PERFORM pg_notify('beep_cache', 'bio:' || COALESCE(NEW.page_id, OLD.page_id));
    RETURN NULL;
END;
$$;
CREATE TRIGGER bio_blocks_cache AFTER INSERT OR UPDATE OR DELETE ON bio_blocks
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_bio_blocks();

-- ---------- Bêta privée : inscription sur invitation ----------
CREATE TABLE beta_invitations (
    id         uuid        PRIMARY KEY,
    email      text        NOT NULL UNIQUE CHECK (email = lower(email)),
    invited_by text,                                                -- identifiant du membre du staff
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);
CREATE INDEX beta_invitations_created_idx ON beta_invitations (created_at DESC, id DESC);
