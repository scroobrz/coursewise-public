-- V1: initial schema — users table only
-- Phase 0 skeleton. All domain tables (courses, reviews, etc.) added in Phase 1+.
-- FR-29: Magic-link auth; no password field — identity is an email address.

CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL    DEFAULT now()
);

-- Fast lookup by email (used on every auth token validation)
CREATE INDEX idx_users_email ON users (email);
