ALTER TABLE notifications ADD COLUMN IF NOT EXISTS user_identity TEXT;
CREATE INDEX IF NOT EXISTS idx_notifications_user_identity ON notifications(user_identity);
