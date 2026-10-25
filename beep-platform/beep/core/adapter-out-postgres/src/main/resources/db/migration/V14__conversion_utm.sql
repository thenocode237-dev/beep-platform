-- Preserve only the five explicitly supported campaign fields, never the full page query.
ALTER TABLE conversion_events ADD COLUMN utm jsonb NOT NULL DEFAULT '{}';
CREATE INDEX conversion_events_campaign_idx ON conversion_events(workspace_id,((utm->>'campaign')),at);
