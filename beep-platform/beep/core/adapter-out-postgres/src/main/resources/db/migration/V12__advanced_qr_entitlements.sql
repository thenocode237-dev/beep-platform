-- QR appearance remains in the existing style JSON document; old records keep their defaults.
UPDATE plans SET entitlements=entitlements||jsonb_build_object('features.qr_advanced',CASE WHEN code IN ('pro','elite') THEN 1 ELSE 0 END);
