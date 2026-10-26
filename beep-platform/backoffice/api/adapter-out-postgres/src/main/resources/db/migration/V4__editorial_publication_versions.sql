-- Separate edit and publication counters; existing draft and publication data is preserved.
ALTER TABLE content_drafts ADD COLUMN published_version integer NOT NULL DEFAULT 0;
UPDATE content_drafts SET published_version=version WHERE status IN ('published','withdrawn');
