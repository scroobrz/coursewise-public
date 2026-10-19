-- V14__allow_shared_device_tokens.sql
-- Drop the UNIQUE constraint on device_token to allow multiple users to share a device.
ALTER TABLE trusted_devices DROP CONSTRAINT IF EXISTS trusted_devices_device_token_key;

-- Add a composite UNIQUE constraint so a user can't trust the same device multiple times.
ALTER TABLE trusted_devices ADD CONSTRAINT trusted_devices_user_id_device_token_key UNIQUE (user_id, device_token);
