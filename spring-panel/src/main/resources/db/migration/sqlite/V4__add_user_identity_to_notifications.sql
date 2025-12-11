ALTER TABLE notifications ADD COLUMN user_identity TEXT;
UPDATE notifications SET user_identity = user WHERE user_identity IS NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user_identity ON notifications(user_identity);
