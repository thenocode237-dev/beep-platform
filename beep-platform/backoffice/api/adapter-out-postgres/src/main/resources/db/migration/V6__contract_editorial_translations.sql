ALTER TABLE content_drafts DROP CONSTRAINT content_drafts_locale_check;
ALTER TABLE content_drafts ADD CONSTRAINT content_drafts_locale_check CHECK(locale IN ('fr','en','es','pt'));
CREATE TRIGGER staff_access_reviews_no_truncate BEFORE TRUNCATE ON staff_access_reviews FOR EACH STATEMENT EXECUTE FUNCTION staff_access_reviews_immutable();
