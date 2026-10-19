-- V16__add_magic_link_purpose.sql
-- Tracks what action a magic link token authorizes, so a token issued for
-- one purpose (e.g. email verification) can't be redeemed for another
-- (e.g. completing 2FA login).
ALTER TABLE magic_links ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'EMAIL_VERIFICATION';
ALTER TABLE magic_links ALTER COLUMN purpose DROP DEFAULT;
