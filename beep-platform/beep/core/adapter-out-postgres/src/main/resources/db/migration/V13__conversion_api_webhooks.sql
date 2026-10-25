-- Phase 3. Additive migration. Secrets are hashed (API) or encrypted (webhooks).
UPDATE plans SET public=true, version=version+1, updated_at=now(), entitlements=entitlements ||
  '{"features.conversion":1,"features.webhooks":1,"features.retargeting":1,"events.monthly_max":100000,"members.max":1}'::jsonb WHERE code='elite';
UPDATE plans SET entitlements=entitlements || '{"members.max":1}'::jsonb WHERE code='pro';
INSERT INTO plan_versions(code,version,snapshot,published_by,published_at)
SELECT code,version,to_jsonb(p),'migration:phase3',now() FROM plans p WHERE code='elite';

CREATE TABLE api_keys (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 name text NOT NULL, prefix text NOT NULL, token_hash text NOT NULL UNIQUE, scopes text[] NOT NULL,
 origins text[] NOT NULL DEFAULT '{}', public_key boolean NOT NULL, revoked_at timestamptz, created_at timestamptz NOT NULL
);
CREATE INDEX api_keys_workspace_idx ON api_keys(workspace_id);
CREATE TABLE api_buckets (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 bucket text NOT NULL, minute timestamptz NOT NULL, count bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(workspace_id,bucket,minute)
);
CREATE TABLE api_idempotency (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 key text NOT NULL, fingerprint text NOT NULL, status integer NOT NULL, content_type text, body text NOT NULL,
 expires_at timestamptz NOT NULL, PRIMARY KEY(workspace_id,key)
);
CREATE TABLE conversion_links (
 link_id uuid PRIMARY KEY REFERENCES links(id) ON DELETE CASCADE,
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 enabled boolean NOT NULL DEFAULT false, model text NOT NULL DEFAULT 'last' CHECK(model IN ('first','last')),
 window_days integer NOT NULL DEFAULT 30 CHECK(window_days BETWEEN 1 AND 90), pixels jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX conversion_links_workspace_idx ON conversion_links(workspace_id);
CREATE TABLE conversion_touches (
 cid text PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 link_id uuid NOT NULL REFERENCES links(id) ON DELETE CASCADE, visitor text, at timestamptz NOT NULL
);
CREATE INDEX conversion_touches_visitor_idx ON conversion_touches(workspace_id,visitor,at);
CREATE TABLE conversion_events (
 id uuid NOT NULL, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 name text NOT NULL, visitor text, cid text, link_id uuid REFERENCES links(id) ON DELETE SET NULL,
 value numeric(18,4) NOT NULL DEFAULT 0 CHECK(value>=0), currency char(3), page text, at timestamptz NOT NULL,
 PRIMARY KEY(workspace_id,id)
);
CREATE INDEX conversion_events_workspace_idx ON conversion_events(workspace_id,at);
CREATE INDEX conversion_events_visitor_idx ON conversion_events(workspace_id,visitor);
CREATE TABLE conversion_usage (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 period timestamptz NOT NULL, count bigint NOT NULL, PRIMARY KEY(workspace_id,period)
);
CREATE TABLE webhook_endpoints (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 url text NOT NULL, events text[] NOT NULL, secret_cipher text NOT NULL, enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL, provider text NOT NULL DEFAULT 'webhook' CHECK(provider IN ('webhook','meta','google')), config_cipher text
);
CREATE INDEX webhook_endpoints_workspace_idx ON webhook_endpoints(workspace_id);
CREATE TABLE webhook_deliveries (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 endpoint_id uuid NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
 event text NOT NULL, payload text NOT NULL, attempts integer NOT NULL DEFAULT 0,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','sending','delivered','failed','cancelled')),
 http_status integer, next_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz
);
CREATE INDEX webhook_deliveries_due_idx ON webhook_deliveries(next_at) WHERE status IN ('pending','sending');
CREATE INDEX webhook_deliveries_workspace_idx ON webhook_deliveries(workspace_id,created_at DESC);
CREATE TABLE pipeline_rejections (minute timestamptz NOT NULL, reason text NOT NULL, count bigint NOT NULL, PRIMARY KEY(minute,reason));

-- Durable outbox is inserted in the same transaction as the business event.
CREATE FUNCTION beep_webhook_enqueue(ws uuid, kind text, event_id text, data jsonb) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO webhook_deliveries(workspace_id,endpoint_id,event,payload)
 SELECT ws,e.id,kind,jsonb_build_object('id',event_id,'type',kind,'createdAt',now(),'data',data)::text
 FROM webhook_endpoints e JOIN workspaces w ON w.id=e.workspace_id JOIN plans p ON p.code=w.plan_code
 WHERE e.workspace_id=ws AND e.enabled AND kind=ANY(e.events) AND w.status='active'
 AND COALESCE((SELECT value FROM entitlement_overrides WHERE workspace_id=ws AND key='features.webhooks'),(p.entitlements->>'features.webhooks')::bigint,0)=1;
END $$;
CREATE FUNCTION beep_conversion_webhook() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM beep_webhook_enqueue(NEW.workspace_id,NEW.name,NEW.id::text,jsonb_build_object('id',NEW.id,'name',NEW.name,'linkId',NEW.link_id,'value',NEW.value,'currency',NEW.currency,'visitor',NEW.visitor,'at',NEW.at));
 RETURN NEW;
END $$;
CREATE TRIGGER conversion_webhook AFTER INSERT ON conversion_events FOR EACH ROW EXECUTE FUNCTION beep_conversion_webhook();
CREATE FUNCTION beep_resource_webhook() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='links' THEN
  PERFORM beep_webhook_enqueue(NEW.workspace_id,'link.created',NEW.id::text,jsonb_build_object('id',NEW.id,'slug',NEW.slug,'destination',NEW.destination));
 ELSIF TG_TABLE_NAME LIKE 'click_events%' THEN
  IF NOT NEW.is_bot THEN PERFORM beep_webhook_enqueue(NEW.workspace_id,'click',NEW.id::text,jsonb_build_object('linkId',NEW.link_id,'at',NEW.ts)); END IF;
 ELSE
  IF NEW.dns_status IS DISTINCT FROM OLD.dns_status OR NEW.tls_status IS DISTINCT FROM OLD.tls_status THEN
   PERFORM beep_webhook_enqueue(NEW.workspace_id,'domain.status_changed',gen_random_uuid()::text,jsonb_build_object('id',NEW.id,'dns',NEW.dns_status,'tls',NEW.tls_status));
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER link_created_webhook AFTER INSERT ON links FOR EACH ROW EXECUTE FUNCTION beep_resource_webhook();
CREATE TRIGGER click_webhook AFTER INSERT ON click_events FOR EACH ROW EXECUTE FUNCTION beep_resource_webhook();
CREATE TRIGGER domain_changed_webhook AFTER UPDATE ON custom_domains FOR EACH ROW EXECUTE FUNCTION beep_resource_webhook();

-- Creation counters survive deletion; use the subscription monthly anniversary, including annual subscriptions.
CREATE TABLE resource_creations (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 resource text NOT NULL, resource_id uuid NOT NULL, at timestamptz NOT NULL, PRIMARY KEY(resource,resource_id)
);
INSERT INTO resource_creations SELECT workspace_id,'links',id,created_at FROM links;
INSERT INTO resource_creations SELECT workspace_id,'qr',id,created_at FROM qr_codes WHERE kind='dynamic';
CREATE INDEX resource_creations_usage_idx ON resource_creations(workspace_id,resource,at);
CREATE FUNCTION beep_creation_count() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='links' THEN
  INSERT INTO resource_creations VALUES(NEW.workspace_id,'links',NEW.id,NEW.created_at);
 ELSIF NEW.kind='dynamic' THEN
  INSERT INTO resource_creations VALUES(NEW.workspace_id,'qr',NEW.id,NEW.created_at);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER link_creation_count AFTER INSERT ON links FOR EACH ROW EXECUTE FUNCTION beep_creation_count();
CREATE TRIGGER qr_creation_count AFTER INSERT ON qr_codes FOR EACH ROW EXECUTE FUNCTION beep_creation_count();

CREATE FUNCTION beep_monthly_period(ws uuid, event_at timestamptz) RETURNS timestamptz LANGUAGE plpgsql STABLE AS $$
DECLARE anchor timestamptz; months integer; candidate timestamptz;
BEGIN
 SELECT period_start INTO anchor FROM subscriptions WHERE workspace_id=ws AND period_start<=event_at;
 IF anchor IS NULL THEN RETURN date_trunc('month',event_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'; END IF;
 months=(extract(year FROM event_at AT TIME ZONE 'UTC')-extract(year FROM anchor AT TIME ZONE 'UTC'))::integer*12+
        (extract(month FROM event_at AT TIME ZONE 'UTC')-extract(month FROM anchor AT TIME ZONE 'UTC'))::integer;
 candidate=anchor + months * interval '1 month';
 IF candidate>event_at THEN candidate=anchor + greatest(0,months-1) * interval '1 month'; END IF;
 RETURN candidate;
END $$;
