-- Phase 5, additive only: no existing writers or events are changed.
CREATE TABLE enterprise_contracts (
 workspace_id uuid PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
 version integer NOT NULL CHECK(version>0), reference text NOT NULL,
 status text NOT NULL CHECK(status IN ('active','suspended','expired')),
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL CHECK(ends_at>starts_at),
 sla_bp integer NOT NULL CHECK(sla_bp BETWEEN 9000 AND 10000),
 dpa_reference text NOT NULL, dpa_sha256 text NOT NULL CHECK(dpa_sha256 ~ '^[a-f0-9]{64}$'),
 retention_days integer NOT NULL CHECK(retention_days BETWEEN 1 AND 3650),
 ip_allowlist jsonb NOT NULL DEFAULT '[]', brand_name text NOT NULL DEFAULT 'Beep',
 brand_color text NOT NULL DEFAULT '#153D35', sso jsonb NOT NULL DEFAULT '{}',
 sso_secret_cipher text NOT NULL DEFAULT '', updated_by uuid NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE enterprise_sso_states (
 state_hash text PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES enterprise_contracts(workspace_id) ON DELETE CASCADE,
 version integer NOT NULL, verifier_cipher text NOT NULL, nonce text NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE enterprise_sso_identities (
 workspace_id uuid NOT NULL REFERENCES enterprise_contracts(workspace_id) ON DELETE CASCADE,
 issuer text NOT NULL, subject text NOT NULL, user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 PRIMARY KEY(workspace_id,issuer,subject), UNIQUE(workspace_id,user_id)
);
CREATE TABLE enterprise_sso_sessions (
 session_hash text NOT NULL REFERENCES sessions(token_hash) ON DELETE CASCADE,
 workspace_id uuid NOT NULL REFERENCES enterprise_contracts(workspace_id) ON DELETE CASCADE,
 version integer NOT NULL, PRIMARY KEY(session_hash,workspace_id)
);
CREATE TABLE mobile_apps (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 name text NOT NULL, scheme text NOT NULL, android_store text NOT NULL, ios_store text NOT NULL,
 enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL
);
CREATE TABLE deferred_tokens (
 token_hash text PRIMARY KEY, app_id uuid NOT NULL REFERENCES mobile_apps(id) ON DELETE CASCADE,
 payload_cipher text NOT NULL, platform text NOT NULL CHECK(platform IN ('android','ios')),
 expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX deferred_tokens_expiry_idx ON deferred_tokens(expires_at);
CREATE TABLE sdk_releases (
 platform text NOT NULL CHECK(platform IN ('android','ios','typescript')), version text NOT NULL,
 url text NOT NULL, sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 status text NOT NULL CHECK(status IN ('active','deprecated','revoked')), minimum_version text NOT NULL,
 published_by uuid NOT NULL, published_at timestamptz NOT NULL, PRIMARY KEY(platform,version)
);
CREATE TABLE platform_nodes (
 id text PRIMARY KEY, role text NOT NULL CHECK(role IN ('api','redirector','worker','all')),
 region text NOT NULL, heartbeat_at timestamptz NOT NULL, capacity jsonb NOT NULL
);
CREATE TABLE assisted_failovers (
 id uuid PRIMARY KEY, node_id text NOT NULL, requested_by uuid NOT NULL,
 reason text NOT NULL, status text NOT NULL DEFAULT 'requested' CHECK(status IN ('requested','confirmed','cancelled')),
 created_at timestamptz NOT NULL, confirmed_at timestamptz
);
CREATE UNIQUE INDEX assisted_failovers_pending_idx ON assisted_failovers(node_id) WHERE status='requested';
CREATE TABLE compliance_evidence (
 id uuid PRIMARY KEY, framework text NOT NULL CHECK(framework IN ('soc2','iso27001','security_audit','pentest')),
 control text NOT NULL, url text NOT NULL, sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 collected_by uuid NOT NULL, collected_at timestamptz NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE access_reviews (
 id uuid PRIMARY KEY, workspace_id uuid REFERENCES workspaces(id) ON DELETE CASCADE,
 reviewer uuid NOT NULL, snapshot jsonb NOT NULL, decision text NOT NULL CHECK(decision IN ('approved','remediation_required')),
 reason text NOT NULL, reviewed_at timestamptz NOT NULL, due_at timestamptz NOT NULL
);

CREATE TABLE enterprise_sso_pending (mfa_hash text PRIMARY KEY,workspace_id uuid NOT NULL REFERENCES enterprise_contracts(workspace_id) ON DELETE CASCADE,version integer NOT NULL,expires_at timestamptz NOT NULL);

CREATE TABLE shared_rate_limits (key text PRIMARY KEY,hits bigint NOT NULL,expires_at timestamptz NOT NULL);
CREATE INDEX shared_rate_limits_expiry_idx ON shared_rate_limits(expires_at);

CREATE OR REPLACE FUNCTION beep_effective_right(ws uuid,k text) RETURNS bigint LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT CASE WHEN k='analytics.retention_days' THEN retention_days::bigint WHEN k='features.bio_badge_removable' AND brand_name<>'Beep' THEN 1 END FROM enterprise_contracts c WHERE c.workspace_id=ws AND c.status='active' AND c.starts_at<=now() AND c.ends_at>now()),(SELECT value FROM entitlement_overrides WHERE workspace_id=ws AND key=k),(p.entitlements->>k)::bigint,0)
 FROM workspaces w JOIN plans p ON p.code=w.plan_code WHERE w.id=ws AND w.status='active'
$$;
CREATE OR REPLACE FUNCTION beep_conversion_retention(event_at timestamptz) RETURNS void LANGUAGE plpgsql AS $$
DECLARE item record; days bigint;
BEGIN
 FOR item IN SELECT w.id,COALESCE((SELECT retention_days FROM enterprise_contracts c WHERE c.workspace_id=w.id AND c.status='active' AND c.starts_at<=event_at AND c.ends_at>event_at),o.value,(p.entitlements->>'analytics.retention_days')::bigint,30) AS retention FROM workspaces w JOIN plans p ON p.code=w.plan_code LEFT JOIN entitlement_overrides o ON o.workspace_id=w.id AND o.key='analytics.retention_days' LOOP
  days=item.retention;
  IF days>=0 THEN
   DELETE FROM conversion_daily WHERE workspace_id=item.id AND day<(event_at AT TIME ZONE 'UTC')::date-days::integer;
   DELETE FROM conversion_events WHERE workspace_id=item.id AND at<event_at-days*interval '1 day';
   DELETE FROM conversion_touches WHERE workspace_id=item.id AND at<event_at-least(days,90)*interval '1 day';
  END IF;
 END LOOP;
END $$;
