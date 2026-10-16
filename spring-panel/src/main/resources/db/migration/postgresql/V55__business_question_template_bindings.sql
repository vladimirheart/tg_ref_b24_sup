-- S4 registry only. Canonical question template payloads remain in bot_settings.
-- No legacy ticket, channel or shared-settings assignment is performed here.
CREATE TABLE business_question_template_bindings (
    business_id BIGINT PRIMARY KEY REFERENCES businesses(id) ON DELETE RESTRICT,
    template_id TEXT NOT NULL UNIQUE CHECK (btrim(template_id) <> ''),
    template_sha256 CHAR(64) NOT NULL CHECK (template_sha256 ~ '^[0-9a-f]{64}$'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
