-- Opt-in after V20, on a TimescaleDB-enabled PostgreSQL 16 primary.
-- Run as migration owner during a maintenance window; all statements are transactional.
-- Existing event data stays in its native partitions, queried through a compatibility view.
BEGIN;
CREATE EXTENSION IF NOT EXISTS timescaledb;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_class WHERE oid='click_events'::regclass AND relkind='p') THEN
  ALTER TABLE click_events RENAME TO click_events_legacy;
 END IF;
END $$;
CREATE TABLE IF NOT EXISTS click_events_timescale (LIKE click_events_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES);
SELECT create_hypertable('click_events_timescale',by_range('ts',INTERVAL '7 days'),if_not_exists=>true);
CREATE OR REPLACE VIEW click_events AS SELECT * FROM click_events_legacy UNION ALL SELECT * FROM click_events_timescale;
CREATE OR REPLACE FUNCTION beep_event_compat_write() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN INSERT INTO click_events_timescale SELECT NEW.*; RETURN NEW; END IF;
 DELETE FROM click_events_legacy WHERE id=OLD.id AND ts=OLD.ts;
 DELETE FROM click_events_timescale WHERE id=OLD.id AND ts=OLD.ts;
 RETURN OLD;
END $$;
DROP TRIGGER IF EXISTS beep_event_compat_write ON click_events;
CREATE TRIGGER beep_event_compat_write INSTEAD OF INSERT OR DELETE ON click_events FOR EACH ROW EXECUTE FUNCTION beep_event_compat_write();
DROP TRIGGER IF EXISTS reject_erased_click ON click_events_timescale;
CREATE TRIGGER reject_erased_click BEFORE INSERT ON click_events_timescale FOR EACH ROW EXECUTE FUNCTION reject_erased_click();
CREATE OR REPLACE FUNCTION ensure_click_partitions(from_month date,months_ahead integer) RETURNS integer LANGUAGE sql AS $$ SELECT 0 $$;
COMMIT;
