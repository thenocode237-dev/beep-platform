-- Phase 4: additive structures. Existing personal workspace IDs and ownership remain stable.
ALTER TABLE workspaces ADD COLUMN personal boolean NOT NULL DEFAULT true;
CREATE TABLE workspace_members (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role text NOT NULL CHECK(role IN ('admin','editor','viewer')),
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(workspace_id,user_id)
);
CREATE INDEX workspace_members_user_idx ON workspace_members(user_id);
CREATE TABLE team_invitations (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 email text NOT NULL, role text NOT NULL CHECK(role IN ('admin','editor','viewer')),
 token_hash text NOT NULL UNIQUE, expires_at timestamptz NOT NULL, accepted_at timestamptz,
 revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX team_invitations_workspace_idx ON team_invitations(workspace_id,email);
CREATE TABLE workspace_audit (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 actor_id uuid REFERENCES users(id) ON DELETE SET NULL,
 action text NOT NULL,target text NOT NULL,at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX workspace_audit_page_idx ON workspace_audit(workspace_id,id DESC);
CREATE TABLE workspace_labels (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('folder','tag')),name text NOT NULL CHECK(length(name) BETWEEN 1 AND 80),
 UNIQUE(workspace_id,kind,name),UNIQUE(workspace_id,id)
);
ALTER TABLE links ADD COLUMN folder_id uuid REFERENCES workspace_labels(id) ON DELETE SET NULL;
CREATE TABLE link_tags (
 link_id uuid NOT NULL REFERENCES links(id) ON DELETE CASCADE,
 tag_id uuid NOT NULL REFERENCES workspace_labels(id) ON DELETE CASCADE,PRIMARY KEY(link_id,tag_id)
);
CREATE TABLE integrations (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 provider text NOT NULL CHECK(provider IN ('stripe','shopify','woocommerce','zapier','make','slack','ga4')),
 name text NOT NULL, config jsonb NOT NULL DEFAULT '{}',credentials_cipher text NOT NULL,
 endpoint_id uuid REFERENCES webhook_endpoints(id) ON DELETE SET NULL,
 status text NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled','error')),
 last_error text,last_at timestamptz,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX integrations_workspace_idx ON integrations(workspace_id);
CREATE TABLE integration_events (
 id uuid PRIMARY KEY, integration_id uuid NOT NULL REFERENCES integrations(id) ON DELETE CASCADE,
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 external_id text NOT NULL,event text NOT NULL,payload jsonb NOT NULL,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','sending','processed','failed','cancelled')),
 attempts integer NOT NULL DEFAULT 0,next_at timestamptz NOT NULL DEFAULT now(),last_error text,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(integration_id,external_id)
);
CREATE INDEX integration_events_due_idx ON integration_events(next_at) WHERE status IN ('pending','sending');
CREATE TABLE destination_health (
 link_id uuid PRIMARY KEY REFERENCES links(id) ON DELETE CASCADE,
 checked_url text, status integer, checked_at timestamptz,next_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE radar_rules (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('click_spike','conversion_drop','broken_link','domain_error')),
 threshold bigint NOT NULL, channel text NOT NULL CHECK(channel IN ('email','slack','webhook')),
 destination_cipher text NOT NULL, link_id uuid REFERENCES links(id) ON DELETE CASCADE,
 endpoint_id uuid REFERENCES webhook_endpoints(id) ON DELETE SET NULL,created_at timestamptz NOT NULL,last_evaluated_at timestamptz
);
CREATE TABLE radar_alerts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 rule_id uuid REFERENCES radar_rules(id) ON DELETE SET NULL,kind text NOT NULL,message text NOT NULL,
 bucket timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(rule_id,bucket)
);
CREATE TABLE scheduled_reports (
 workspace_id uuid PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,frequency text NOT NULL CHECK(frequency IN ('daily','weekly')),
 enabled boolean NOT NULL,next_at timestamptz NOT NULL,updated_at timestamptz NOT NULL
);
CREATE TABLE ecosystem_tasks (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 channel text NOT NULL CHECK(channel IN ('email','slack','webhook')),destination_cipher text NOT NULL,body text NOT NULL,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','sending','delivered','failed','cancelled')),
 attempts integer NOT NULL DEFAULT 0,next_at timestamptz NOT NULL DEFAULT now(),last_error text,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ecosystem_tasks_due_idx ON ecosystem_tasks(next_at) WHERE status IN ('pending','sending');
CREATE TABLE bio_submissions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 page_id uuid NOT NULL REFERENCES bio_pages(id) ON DELETE CASCADE,block_id text NOT NULL,
 email_cipher text NOT NULL,message_cipher text NOT NULL,consent_at timestamptz NOT NULL
);
CREATE INDEX bio_submissions_workspace_idx ON bio_submissions(workspace_id,consent_at DESC);
CREATE TABLE affiliates (
 user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,code text NOT NULL UNIQUE,
 accepted_at timestamptz NOT NULL,rate_bp integer NOT NULL DEFAULT 2000 CHECK(rate_bp BETWEEN 0 AND 10000)
);
CREATE TABLE referrals (
 workspace_id uuid PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
 affiliate_id uuid NOT NULL REFERENCES affiliates(user_id) ON DELETE CASCADE,created_at timestamptz NOT NULL
);
CREATE TABLE affiliate_commissions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),affiliate_id uuid NOT NULL REFERENCES affiliates(user_id) ON DELETE CASCADE,
 payment_id uuid NOT NULL UNIQUE REFERENCES payments(id) ON DELETE CASCADE,
 amount_xaf bigint NOT NULL CHECK(amount_xaf>=0),status text NOT NULL CHECK(status IN ('pending','approved','paid','void')),
 available_at timestamptz NOT NULL,paid_at timestamptz,reference text,recorded_by uuid,created_at timestamptz NOT NULL
);
CREATE TABLE public_content (
 kind text NOT NULL CHECK(kind IN ('blog','roadmap','announcement')),slug text NOT NULL,
 locale text NOT NULL CHECK(locale IN ('fr','en')),title text NOT NULL,body text NOT NULL,
 status text NOT NULL CHECK(status IN ('published','withdrawn')),version integer NOT NULL,
 published_by uuid NOT NULL,published_at timestamptz NOT NULL,PRIMARY KEY(kind,slug,locale)
);
UPDATE plans SET entitlements=entitlements||'{"features.teams":0,"features.integrations":0,"features.radar":0,"features.insights":0,"features.reports":0,"features.bio_advanced":0,"features.workspace_audit":0}'::jsonb WHERE code='starter';
UPDATE plans SET entitlements=entitlements||'{"features.teams":1,"features.integrations":1,"features.radar":1,"features.insights":0,"features.reports":1,"features.bio_advanced":0,"features.workspace_audit":0}'::jsonb WHERE code='pro';
UPDATE plans SET entitlements=entitlements||'{"features.teams":1,"features.integrations":1,"features.radar":1,"features.insights":1,"features.reports":1,"features.bio_advanced":1,"features.workspace_audit":1}'::jsonb WHERE code='elite';
UPDATE plans SET version=version+1,updated_at=now(),entitlements=entitlements||jsonb_build_object('members.max',CASE code WHEN 'pro' THEN 3 ELSE 15 END) WHERE code IN ('pro','elite');
INSERT INTO plan_versions(code,version,snapshot,published_by,published_at) SELECT code,version,to_jsonb(p),'migration:phase4',now() FROM plans p WHERE code IN ('pro','elite');
CREATE FUNCTION beep_effective_right(ws uuid,k text) RETURNS bigint LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT value FROM entitlement_overrides WHERE workspace_id=ws AND key=k),(p.entitlements->>k)::bigint,0)
 FROM workspaces w JOIN plans p ON p.code=w.plan_code WHERE w.id=ws AND w.status='active'
$$;
CREATE OR REPLACE FUNCTION beep_webhook_enqueue(ws uuid,kind text,event_id text,data jsonb) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO webhook_deliveries(workspace_id,endpoint_id,event,payload)
 SELECT ws,e.id,kind,jsonb_build_object('id',event_id,'type',kind,'createdAt',now(),'data',data)::text FROM webhook_endpoints e
 WHERE e.workspace_id=ws AND e.enabled AND kind=ANY(e.events) AND
 (beep_effective_right(ws,'features.webhooks')=1 OR
 (beep_effective_right(ws,'features.integrations')=1 AND EXISTS(SELECT 1 FROM integrations i WHERE i.endpoint_id=e.id AND i.status<>'disabled' AND i.provider IN ('zapier','make'))));
END $$;
CREATE FUNCTION beep_workspace_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND NOT (NEW.actor_id IS NULL AND OLD.actor_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM users WHERE id=OLD.actor_id) AND (to_jsonb(OLD)-'actor_id')=(to_jsonb(NEW)-'actor_id')) THEN RAISE EXCEPTION 'workspace audit is append only'; END IF;
 IF TG_OP='DELETE' AND EXISTS(SELECT 1 FROM workspaces WHERE id=OLD.workspace_id) THEN RAISE EXCEPTION 'workspace audit is append only'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER workspace_audit_immutable BEFORE UPDATE OR DELETE ON workspace_audit FOR EACH ROW EXECUTE FUNCTION beep_workspace_audit_immutable();
