CREATE TABLE IF NOT EXISTS workforce_positions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    check_in_required BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_check_in BOOLEAN NOT NULL DEFAULT FALSE,
    notification_channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    notification_target VARCHAR(32) NOT NULL DEFAULT 'support_chat'
        CHECK (notification_target IN ('support_chat', 'broadcast_channel', 'custom_chat')),
    notification_chat_id TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workforce_user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    position_id BIGINT REFERENCES workforce_positions(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    check_in_required_override BOOLEAN,
    notify_on_check_in_override BOOLEAN,
    notification_channel_id_override BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    notification_target_override VARCHAR(32)
        CHECK (notification_target_override IS NULL OR notification_target_override IN ('support_chat', 'broadcast_channel', 'custom_chat')),
    notification_chat_id_override TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workforce_schedule_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME WITHOUT TIME ZONE NOT NULL,
    end_time TIME WITHOUT TIME ZONE NOT NULL,
    effective_from DATE,
    effective_to DATE,
    check_in_open_minutes INTEGER NOT NULL DEFAULT 120 CHECK (check_in_open_minutes BETWEEN 0 AND 720),
    late_after_minutes INTEGER NOT NULL DEFAULT 15 CHECK (late_after_minutes BETWEEN 0 AND 720),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_workforce_schedule_user_day
    ON workforce_schedule_rules(user_id, day_of_week, active);
CREATE INDEX IF NOT EXISTS idx_workforce_schedule_effective
    ON workforce_schedule_rules(user_id, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS workforce_shift_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_key VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schedule_rule_id BIGINT REFERENCES workforce_schedule_rules(id) ON DELETE SET NULL,
    position_id BIGINT REFERENCES workforce_positions(id) ON DELETE SET NULL,
    shift_date DATE NOT NULL,
    scheduled_start_at TIMESTAMP WITH TIME ZONE,
    scheduled_end_at TIMESTAMP WITH TIME ZONE,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    check_in_status VARCHAR(32) NOT NULL
        CHECK (check_in_status IN ('on_time', 'late', 'unscheduled')),
    check_in_source VARCHAR(32) NOT NULL DEFAULT 'login',
    full_name_snapshot VARCHAR(512),
    position_name_snapshot VARCHAR(255),
    notification_required BOOLEAN NOT NULL DEFAULT FALSE,
    notification_channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    notification_target VARCHAR(32),
    notification_chat_id TEXT,
    notification_status VARCHAR(32) NOT NULL DEFAULT 'skipped'
        CHECK (notification_status IN ('skipped', 'pending', 'sent', 'failed')),
    notification_sent_at TIMESTAMP WITH TIME ZONE,
    notification_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workforce_shift_user_checked_in
    ON workforce_shift_sessions(user_id, checked_in_at DESC);
CREATE INDEX IF NOT EXISTS idx_workforce_shift_date_status
    ON workforce_shift_sessions(shift_date, check_in_status);
