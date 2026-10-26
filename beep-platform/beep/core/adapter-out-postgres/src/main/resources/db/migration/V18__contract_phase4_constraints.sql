-- Apply after readers/writers supporting phase 4 are deployed. No row is removed.
-- The new writer uses the partial owner conflict target and accepts the expanded provider/block enums.
CREATE UNIQUE INDEX workspaces_personal_owner_key ON workspaces(owner_id) WHERE personal;
DROP INDEX workspaces_owner_key;
ALTER TABLE webhook_endpoints DROP CONSTRAINT webhook_endpoints_provider_check;
ALTER TABLE webhook_endpoints ADD CONSTRAINT webhook_endpoints_provider_check CHECK(provider IN ('webhook','meta','google','slack'));
ALTER TABLE bio_blocks DROP CONSTRAINT bio_blocks_type_check;
ALTER TABLE bio_blocks ADD CONSTRAINT bio_blocks_type_check CHECK(type IN ('link','button','heading','text','image','social','email','form','video','audio'));
