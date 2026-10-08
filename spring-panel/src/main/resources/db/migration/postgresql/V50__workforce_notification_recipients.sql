CREATE TABLE IF NOT EXISTS workforce_position_notification_recipients (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id BIGINT NOT NULL REFERENCES workforce_positions(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    target VARCHAR(32) NOT NULL CHECK (target IN ('support_chat', 'broadcast_channel', 'custom_chat')),
    chat_id TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((target = 'custom_chat' AND NULLIF(BTRIM(chat_id), '') IS NOT NULL)
        OR (target <> 'custom_chat' AND chat_id IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workforce_position_recipient_route
    ON workforce_position_notification_recipients(position_id, channel_id, target, COALESCE(chat_id, ''));
CREATE INDEX IF NOT EXISTS idx_workforce_position_recipients_position
    ON workforce_position_notification_recipients(position_id, id);

INSERT INTO workforce_position_notification_recipients(position_id, channel_id, target, chat_id)
SELECT id, notification_channel_id, notification_target,
       CASE WHEN notification_target = 'custom_chat' THEN NULLIF(BTRIM(notification_chat_id), '') ELSE NULL END
FROM workforce_positions
WHERE notification_channel_id IS NOT NULL
  AND (notification_target <> 'custom_chat' OR NULLIF(BTRIM(notification_chat_id), '') IS NOT NULL)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS workforce_shift_notification_deliveries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_session_id BIGINT NOT NULL REFERENCES workforce_shift_sessions(id) ON DELETE CASCADE,
    channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    target VARCHAR(32) NOT NULL,
    chat_id TEXT,
    resolved_recipient TEXT,
    delivery_status VARCHAR(32) NOT NULL CHECK (delivery_status IN ('sent', 'failed')),
    error TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workforce_shift_notification_deliveries_session
    ON workforce_shift_notification_deliveries(shift_session_id, id);
