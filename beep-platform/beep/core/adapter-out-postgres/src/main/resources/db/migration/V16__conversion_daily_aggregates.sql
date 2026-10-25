-- Anonymous daily totals survive raw-event retention. No visitor/click receipt is stored here.
CREATE TABLE conversion_daily (
 workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 day date NOT NULL, dimension text NOT NULL, key_hash text NOT NULL, key text NOT NULL,
 currency text NOT NULL DEFAULT '', events bigint NOT NULL DEFAULT 0, leads bigint NOT NULL DEFAULT 0,
 sales bigint NOT NULL DEFAULT 0, revenue numeric(24,4) NOT NULL DEFAULT 0, visitors bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(workspace_id,day,dimension,key_hash,currency)
);
CREATE INDEX conversion_daily_workspace_idx ON conversion_daily(workspace_id,dimension,day);
CREATE FUNCTION beep_conversion_total(ws uuid, bucket date, dim text, item text, money text,
 n bigint, l bigint, s bigint, amount numeric, visitors_count bigint) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO conversion_daily VALUES(ws,bucket,dim,encode(sha256(convert_to(item,'UTF8')),'hex'),item,COALESCE(money,''),n,l,s,amount,visitors_count)
 ON CONFLICT(workspace_id,day,dimension,key_hash,currency) DO UPDATE SET
 events=conversion_daily.events+excluded.events,leads=conversion_daily.leads+excluded.leads,
 sales=conversion_daily.sales+excluded.sales,revenue=conversion_daily.revenue+excluded.revenue,
 visitors=conversion_daily.visitors+excluded.visitors;
END $$;

-- Backfill the raw history available at rollout, once, before installing triggers.
INSERT INTO conversion_daily
 SELECT workspace_id,(at AT TIME ZONE 'UTC')::date,'link',encode(sha256(convert_to(COALESCE(link_id::text,''),'UTF8')),'hex'),COALESCE(link_id::text,''),COALESCE(currency,''),
 count(*),count(*) FILTER(WHERE name='lead'),count(*) FILTER(WHERE name='sale'),COALESCE(sum(value) FILTER(WHERE name='sale'),0),0
 FROM conversion_events GROUP BY workspace_id,(at AT TIME ZONE 'UTC')::date,link_id,currency;
INSERT INTO conversion_daily
 SELECT workspace_id,(at AT TIME ZONE 'UTC')::date,'page',encode(sha256(convert_to(page,'UTF8')),'hex'),page,'',count(*),0,0,0,count(DISTINCT visitor)
 FROM conversion_events WHERE name='pageview' AND page IS NOT NULL GROUP BY workspace_id,(at AT TIME ZONE 'UTC')::date,page;
INSERT INTO conversion_daily
 SELECT workspace_id,(at AT TIME ZONE 'UTC')::date,'campaign',encode(sha256(convert_to(jsonb_build_object('source',utm->>'source','campaign',utm->>'campaign')::text,'UTF8')),'hex'),
 jsonb_build_object('source',utm->>'source','campaign',utm->>'campaign')::text,COALESCE(currency,''),count(*),count(*) FILTER(WHERE name='lead'),count(*) FILTER(WHERE name='sale'),COALESCE(sum(value) FILTER(WHERE name='sale'),0),0
 FROM conversion_events WHERE utm<>'{}'::jsonb GROUP BY workspace_id,(at AT TIME ZONE 'UTC')::date,utm->>'source',utm->>'campaign',currency;
INSERT INTO conversion_daily
 SELECT workspace_id,(at AT TIME ZONE 'UTC')::date,'click',encode(sha256(convert_to(link_id::text,'UTF8')),'hex'),link_id::text,'',count(*),0,0,0,0
 FROM conversion_touches GROUP BY workspace_id,(at AT TIME ZONE 'UTC')::date,link_id;

CREATE FUNCTION beep_conversion_aggregate_event() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE bucket date; lead_count bigint; sale_count bigint; amount numeric; visitor_count bigint;
BEGIN
 bucket=(NEW.at AT TIME ZONE 'UTC')::date;
 lead_count=CASE WHEN NEW.name='lead' THEN 1 ELSE 0 END;
 sale_count=CASE WHEN NEW.name='sale' THEN 1 ELSE 0 END;
 amount=CASE WHEN NEW.name='sale' THEN NEW.value ELSE 0 END;
 PERFORM beep_conversion_total(NEW.workspace_id,bucket,'link',COALESCE(NEW.link_id::text,''),NEW.currency,1,lead_count,sale_count,amount,0);
 IF NEW.name='pageview' AND NEW.page IS NOT NULL THEN
  visitor_count=0;
  IF NEW.visitor IS NOT NULL AND NOT EXISTS(SELECT 1 FROM conversion_events e WHERE e.workspace_id=NEW.workspace_id AND e.visitor=NEW.visitor AND e.page=NEW.page AND e.name='pageview' AND (e.at AT TIME ZONE 'UTC')::date=bucket AND e.id<>NEW.id) THEN visitor_count=1; END IF;
  PERFORM beep_conversion_total(NEW.workspace_id,bucket,'page',NEW.page,'',1,0,0,0,visitor_count);
 END IF;
 IF NEW.utm<>'{}'::jsonb THEN
  PERFORM beep_conversion_total(NEW.workspace_id,bucket,'campaign',jsonb_build_object('source',NEW.utm->>'source','campaign',NEW.utm->>'campaign')::text,NEW.currency,1,lead_count,sale_count,amount,0);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER conversion_aggregate_event AFTER INSERT ON conversion_events FOR EACH ROW EXECUTE FUNCTION beep_conversion_aggregate_event();
CREATE FUNCTION beep_conversion_aggregate_click() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM beep_conversion_total(NEW.workspace_id,(NEW.at AT TIME ZONE 'UTC')::date,'click',NEW.link_id::text,'',1,0,0,0,0);
 RETURN NEW;
END $$;
CREATE TRIGGER conversion_aggregate_click AFTER INSERT ON conversion_touches FOR EACH ROW EXECUTE FUNCTION beep_conversion_aggregate_click();

CREATE FUNCTION beep_conversion_retention(event_at timestamptz) RETURNS void LANGUAGE plpgsql AS $$
DECLARE item record; days bigint;
BEGIN
 FOR item IN SELECT w.id,COALESCE(o.value,(p.entitlements->>'analytics.retention_days')::bigint,30) AS retention FROM workspaces w JOIN plans p ON p.code=w.plan_code LEFT JOIN entitlement_overrides o ON o.workspace_id=w.id AND o.key='analytics.retention_days' LOOP
  days=item.retention;
  IF days>=0 THEN
   DELETE FROM conversion_daily WHERE workspace_id=item.id AND day<(event_at AT TIME ZONE 'UTC')::date-days::integer;
   DELETE FROM conversion_events WHERE workspace_id=item.id AND at<event_at-days*interval '1 day';
   DELETE FROM conversion_touches WHERE workspace_id=item.id AND at<event_at-least(days,90)*interval '1 day';
  END IF;
 END LOOP;
END $$;
