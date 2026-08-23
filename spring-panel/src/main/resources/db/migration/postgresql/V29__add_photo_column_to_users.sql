-- PostgreSQL parity for panel-user avatar persistence.
-- SQLite already has the equivalent users.photo migration (V35).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS photo TEXT;
