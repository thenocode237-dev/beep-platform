CREATE TABLE staff_access_reviews (
 id uuid PRIMARY KEY,reviewer uuid NOT NULL,snapshot jsonb NOT NULL,
 decision text NOT NULL CHECK(decision IN ('approved','remediation_required')),
 reason text NOT NULL,reviewed_at timestamptz NOT NULL,due_at timestamptz NOT NULL
);
CREATE FUNCTION staff_access_reviews_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'access reviews are append only'; END $$;
CREATE TRIGGER staff_access_reviews_immutable BEFORE UPDATE OR DELETE ON staff_access_reviews FOR EACH STATEMENT EXECUTE FUNCTION staff_access_reviews_immutable();
