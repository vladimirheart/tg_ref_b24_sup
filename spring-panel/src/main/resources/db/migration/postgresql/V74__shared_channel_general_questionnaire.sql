-- Separate opt-in for a GENERAL choice in a shared channel, not a general-only channel.
-- Empty by design: no existing channel, template, ticket or grant is changed.
CREATE TABLE shared_channel_general_question_template_bindings (
    channel_id BIGINT PRIMARY KEY REFERENCES channels(id) ON DELETE RESTRICT,
    template_id TEXT NOT NULL UNIQUE CHECK (btrim(template_id) <> '' AND template_id = btrim(template_id)),
    template_sha256 CHAR(64) NOT NULL CHECK (template_sha256 ~ '^[0-9a-f]{64}$'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
