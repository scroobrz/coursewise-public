-- V4__add_username_to_users.sql

-- 1. Add the column allowing NULL initially
ALTER TABLE users ADD COLUMN username VARCHAR(255);

-- 2. Backfill existing rows by extracting the part before '@' in the email
UPDATE users SET username = split_part(email, '@', 1);

-- 3. Enforce constraints
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
