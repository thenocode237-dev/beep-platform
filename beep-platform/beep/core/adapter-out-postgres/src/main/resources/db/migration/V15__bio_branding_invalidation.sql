-- Phase 3: the published Elite entitlement removes the bio badge. Invalidate cached renders when rights change.
CREATE OR REPLACE FUNCTION beep_cache_notify_workspaces() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status OR NEW.plan_code IS DISTINCT FROM OLD.plan_code THEN
  PERFORM pg_notify('beep_cache','ws:'||NEW.id);
 END IF;
 RETURN NULL;
END $$;
CREATE FUNCTION beep_cache_notify_entitlements() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_notify('beep_cache','entitlements:changed');
 RETURN NULL;
END $$;
CREATE TRIGGER plan_entitlements_cache AFTER UPDATE OF entitlements ON plans FOR EACH STATEMENT EXECUTE FUNCTION beep_cache_notify_entitlements();
CREATE TRIGGER override_entitlements_cache AFTER INSERT OR UPDATE OR DELETE ON entitlement_overrides FOR EACH STATEMENT EXECUTE FUNCTION beep_cache_notify_entitlements();
