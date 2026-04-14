-- This migration is intentionally a no-op.
-- SQLite lacks a conditional ALTER TABLE ADD COLUMN, so the safe schema change
-- is handled in the Java migration V4_1__add_user_identity_to_notifications.
SELECT 1;
