-- A general-only channel is opt-in. Missing business bindings do not imply GENERAL.
-- The template remains canonical in bot_settings and is selected by the channel.
CREATE TABLE general_channel_question_template_bindings (
    channel_id BIGINT PRIMARY KEY REFERENCES channels(id) ON DELETE RESTRICT,
    template_id TEXT NOT NULL CHECK (btrim(template_id) <> ''),
    template_sha256 CHAR(64) NOT NULL CHECK (template_sha256 ~ '^[0-9a-f]{64}$'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
