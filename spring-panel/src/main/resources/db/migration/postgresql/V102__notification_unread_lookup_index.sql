-- The sidebar asks for an unread count frequently when realtime delivery is unavailable.
-- Narrow the candidate set before the business-scope predicate is evaluated.
CREATE INDEX IF NOT EXISTS idx_notifications_unread_recipient
    ON notifications (user_identity)
    WHERE is_read = FALSE AND shared_work_kind IS NULL;
