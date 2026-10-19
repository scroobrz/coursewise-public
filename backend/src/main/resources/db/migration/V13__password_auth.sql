-- V13__password_auth.sql
-- Upgrade from magic-link-only to password + magic-link 2FA (FR-29, D2).

-- Add password hash and email verification status to users.
-- password_hash is nullable during migration to allow existing users
-- (dev seeds) to remain until the seeder is updated with hashed passwords.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing users as email-verified (they already authenticated via magic link).
UPDATE users SET email_verified = TRUE;

-- Trusted devices table for "remember this device" 2FA bypass.
-- When a user checks "Trust this device", a long-lived token is stored here
-- and sent as a cookie. On subsequent logins from that device, the 2FA
-- magic-link step is skipped entirely.
CREATE TABLE trusted_devices (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token VARCHAR(255) NOT NULL UNIQUE,
    device_name  VARCHAR(255),           -- e.g. "Chrome on macOS"
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Fast lookup by user_id (listing / revoking devices)
CREATE INDEX idx_trusted_devices_user_id ON trusted_devices(user_id);
-- Fast lookup by token (validating on login)
CREATE INDEX idx_trusted_devices_token ON trusted_devices(device_token);
