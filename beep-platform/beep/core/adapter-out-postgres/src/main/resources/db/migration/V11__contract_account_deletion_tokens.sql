-- Additive widening of a CHECK; same exception as V5, no data removed.
ALTER TABLE one_time_tokens DROP CONSTRAINT one_time_tokens_purpose_check;
ALTER TABLE one_time_tokens ADD CONSTRAINT one_time_tokens_purpose_check CHECK (purpose IN ('verify_email','reset_password','magic_link','mfa_pending','erase_account'));
