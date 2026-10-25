-- Additive phase 2: moderation, flags, privacy and quota lifecycle.
CREATE TABLE abuse_reports (
  id uuid PRIMARY KEY, url text NOT NULL, category text NOT NULL, detail text NOT NULL,
  source text NOT NULL, status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','dismissed','blocked')),
  reason text, created_at timestamptz NOT NULL, resolved_at timestamptz, resolved_by text
);
CREATE INDEX abuse_reports_queue ON abuse_reports(status, created_at, id);
CREATE UNIQUE INDEX abuse_reports_open_auto ON abuse_reports(url) WHERE source = 'safe_browsing' AND status = 'open';
CREATE TABLE blocked_hosts (host text PRIMARY KEY, reason text NOT NULL, created_at timestamptz NOT NULL, updated_by text);
CREATE TABLE feature_flags (
  key text PRIMARY KEY, enabled boolean NOT NULL, rollout_percent integer NOT NULL CHECK (rollout_percent BETWEEN 0 AND 100),
  version integer NOT NULL DEFAULT 1, reason text NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(), updated_by text
);
INSERT INTO feature_flags(key, enabled, rollout_percent, reason) VALUES
 ('links.rules', true, 100, 'Phase 2'), ('deeplinks.simulator', true, 100, 'Phase 2');
CREATE TABLE trust_audit (id bigserial PRIMARY KEY, at timestamptz NOT NULL DEFAULT now(), actor text NOT NULL, action text NOT NULL, target text NOT NULL, reason text);
CREATE FUNCTION trust_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'append-only'; END $$;
CREATE TRIGGER trust_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON trust_audit FOR EACH STATEMENT EXECUTE FUNCTION trust_audit_immutable();
ALTER TABLE links ADD COLUMN quota_exceeded_at timestamptz;
ALTER TABLE links ADD COLUMN quota_notified_at timestamptz;
ALTER TABLE links ADD COLUMN safety_checked_at timestamptz;
CREATE INDEX links_safety_queue ON links(safety_checked_at NULLS FIRST, updated_at);
-- Separate accounting archive: no foreign keys to a live account. Access is staff-only.
CREATE TABLE accounting_archive (number text PRIMARY KEY, invoice jsonb NOT NULL, payment jsonb NOT NULL, archived_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE erased_workspaces (workspace_id uuid PRIMARY KEY, erased_at timestamptz NOT NULL DEFAULT now());
CREATE FUNCTION reject_erased_click() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM pg_advisory_xact_lock(hashtextextended(NEW.workspace_id::text,17)); IF EXISTS(SELECT 1 FROM erased_workspaces WHERE workspace_id = NEW.workspace_id) THEN RETURN NULL; END IF; RETURN NEW; END $$;
CREATE TRIGGER reject_erased_click BEFORE INSERT ON click_events FOR EACH ROW EXECUTE FUNCTION reject_erased_click();


CREATE FUNCTION beep_destination_hosts(destination text,rules jsonb) RETURNS SETOF text LANGUAGE sql IMMUTABLE AS $$
 SELECT DISTINCT lower(substring(url from '^https?://([^/:?#]+)')) FROM (
  SELECT destination AS url
  UNION ALL SELECT value FROM jsonb_each_text(coalesce(rules->'onelink','{}'::jsonb))
  UNION ALL SELECT value->>'url' FROM jsonb_array_elements(coalesce(rules->'routes','[]'::jsonb))
  UNION ALL SELECT value->>'url' FROM jsonb_array_elements(coalesce(rules->'variants','[]'::jsonb))
 ) urls WHERE url IS NOT NULL
$$;

