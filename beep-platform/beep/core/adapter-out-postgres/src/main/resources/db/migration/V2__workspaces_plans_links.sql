-- Phase 1 · cycle 1 : workspaces, plans et droits, liens courts, registre des slugs, invalidation du cache de redirection.
-- Expand uniquement : aucune donnée existante n'est modifiée ni supprimée.

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- recherche staff par sous-chaîne (extension « trusted » : le propriétaire de la base suffit)

-- Comptes sans mot de passe (lien magique, OAuth) : le hachage devient facultatif.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- ---------- Plans publiés (copie lue à l'exécution ; brouillons et versionnement dans le back office, phase 2) ----------
CREATE TABLE plans (
    code         text        PRIMARY KEY,
    name         text        NOT NULL,
    version      integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    price_xaf    bigint      NOT NULL DEFAULT 0 CHECK (price_xaf >= 0),   -- prix mensuel TTC (FCFA)
    public       boolean     NOT NULL DEFAULT false,                       -- commercialisé (sinon « bientôt »)
    entitlements jsonb       NOT NULL,                                     -- clés §6.2 : -1 = illimité, fonctionnalités 0/1
    updated_at   timestamptz NOT NULL DEFAULT now()
);

INSERT INTO plans (code, name, version, price_xaf, public, entitlements) VALUES
  ('starter', 'Starter', 1, 0, true,
   '{"links.stock_max":5,"qr.stock_max":5,"bio.max":1,"domains.max":0,"members.max":1,"api.rpm":0,"analytics.retention_days":30,
     "features.analytics_advanced":0,"features.bio_badge_removable":0,"features.export_csv":0}'),
  ('pro', 'Pro', 1, 4999, false,
   '{"links.stock_max":50,"qr.stock_max":50,"bio.max":3,"domains.max":1,"members.max":3,"api.rpm":60,"analytics.retention_days":-1,
     "features.analytics_advanced":1,"features.bio_badge_removable":0,"features.export_csv":1}'),
  ('elite', 'Elite', 1, 14999, false,
   '{"links.monthly_creations":500,"qr.monthly_creations":500,"bio.max":10,"domains.max":10,"members.max":15,"api.rpm":600,
     "analytics.retention_days":-1,"features.analytics_advanced":1,"features.bio_badge_removable":1,"features.export_csv":1}');

-- ---------- Workspaces (phase 1 : un workspace personnel par utilisateur) ----------
CREATE TABLE workspaces (
    id            uuid        PRIMARY KEY,
    owner_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          text        NOT NULL,
    plan_code     text        NOT NULL REFERENCES plans (code),
    status        text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    first_link_at timestamptz,                                   -- mesure de l'activation
    created_at    timestamptz NOT NULL
);
CREATE UNIQUE INDEX workspaces_owner_key ON workspaces (owner_id);   -- relâché avec les équipes (phase 4)

CREATE TABLE entitlement_overrides (
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    key          text        NOT NULL,
    value        bigint      NOT NULL,
    reason       text        NOT NULL,
    updated_by   text,                                           -- identifiant du membre du staff
    updated_at   timestamptz NOT NULL,
    PRIMARY KEY (workspace_id, key)
);

-- ---------- Registre des slugs : un chemin public désigne un lien OU une page bio ----------
CREATE TABLE slugs (
    slug      text PRIMARY KEY,
    kind      text NOT NULL CHECK (kind IN ('link', 'bio')),
    target_id uuid NOT NULL
);

-- ---------- Liens courts ----------
CREATE TABLE links (
    id              uuid        PRIMARY KEY,
    workspace_id    uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    slug            text        NOT NULL UNIQUE,
    destination     text        NOT NULL,
    title           text,
    status          text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    disabled_reason text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);
CREATE INDEX links_workspace_idx ON links (workspace_id, created_at DESC, id DESC);
CREATE INDEX links_created_idx ON links (created_at DESC, id DESC);
CREATE INDEX links_slug_trgm ON links USING gin (slug gin_trgm_ops);
CREATE INDEX links_destination_trgm ON links USING gin (destination gin_trgm_ops);
CREATE INDEX users_email_trgm ON users USING gin (email gin_trgm_ops);

-- ---------- Invalidation du cache de redirection entre instances (LISTEN beep_cache) ----------
-- Notification envoyée au COMMIT de la transaction qui modifie : aucune instance ne sert une valeur périmée au-delà de ce délai.
CREATE FUNCTION beep_cache_notify_slugs() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN PERFORM pg_notify('beep_cache', 'slug:' || OLD.slug); END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN PERFORM pg_notify('beep_cache', 'slug:' || NEW.slug); END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER slugs_cache AFTER INSERT OR UPDATE OR DELETE ON slugs
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_slugs();

CREATE FUNCTION beep_cache_notify_links() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify('beep_cache', 'slug:' || OLD.slug);
    PERFORM pg_notify('beep_cache', 'link:' || OLD.id);
    IF TG_OP = 'UPDATE' THEN PERFORM pg_notify('beep_cache', 'slug:' || NEW.slug); END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER links_cache AFTER UPDATE OR DELETE ON links
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_links();

CREATE FUNCTION beep_cache_notify_workspaces() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN PERFORM pg_notify('beep_cache', 'ws:' || NEW.id); END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER workspaces_cache AFTER UPDATE ON workspaces
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_workspaces();
