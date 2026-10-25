-- Phase 2 · cycle 5 : domaines personnalisés. Expand uniquement.

CREATE TABLE custom_domains (
    id                uuid        PRIMARY KEY,
    workspace_id      uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    hostname          text        NOT NULL,
    dns_status        text        NOT NULL DEFAULT 'pending' CHECK (dns_status IN ('pending', 'verified', 'failed')),
    tls_status        text        NOT NULL DEFAULT 'pending' CHECK (tls_status IN ('pending', 'active')),
    verify_token      text        NOT NULL,
    verified_at       timestamptz,
    last_check_at     timestamptz,
    last_error        text,
    is_default        boolean     NOT NULL DEFAULT false,
    root_redirect_url text,
    not_found_url     text,
    blocked           boolean     NOT NULL DEFAULT false,
    blocked_reason    text,
    created_at        timestamptz NOT NULL,
    CONSTRAINT custom_domains_hostname_lower CHECK (hostname = lower(hostname))
);
CREATE UNIQUE INDEX custom_domains_hostname_key ON custom_domains (hostname);
CREATE UNIQUE INDEX custom_domains_default_key ON custom_domains (workspace_id) WHERE is_default;
CREATE INDEX custom_domains_workspace_idx ON custom_domains (workspace_id, created_at);
CREATE INDEX custom_domains_hostname_trgm ON custom_domains USING gin (hostname gin_trgm_ops);

ALTER TABLE links ADD COLUMN domain_id uuid REFERENCES custom_domains (id);
CREATE INDEX links_domain_idx ON links (domain_id) WHERE domain_id IS NOT NULL;

-- Invalidation du routage par hôte entre instances (canal beep_cache, charge utile `domain:<hôte>`).
CREATE FUNCTION beep_cache_notify_domains() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN PERFORM pg_notify('beep_cache', 'domain:' || OLD.hostname); END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN PERFORM pg_notify('beep_cache', 'domain:' || NEW.hostname); END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER custom_domains_cache AFTER INSERT OR UPDATE OR DELETE ON custom_domains
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_domains();
