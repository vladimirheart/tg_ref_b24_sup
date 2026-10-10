-- 01-278 S1: canonical multibusiness foundation.
-- This migration is additive. It intentionally does not assign legacy rows,
-- enable strict filtering, or change legacy text fields.

CREATE TABLE IF NOT EXISTS businesses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'archived')),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO businesses(code, display_name, status)
VALUES
    ('sushi', 'Суши', 'active'),
    ('bliny', 'Блины', 'active')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS business_memberships (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    role_code VARCHAR(32) NOT NULL
        CHECK (role_code IN ('VIEWER', 'OPERATOR', 'MANAGER', 'ADMIN')),
    permission_overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, business_id)
);

CREATE INDEX IF NOT EXISTS idx_business_memberships_business_user
    ON business_memberships(business_id, user_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS business_access_grants (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grant_scope VARCHAR(32) NOT NULL
        CHECK (grant_scope = 'ALL_BUSINESSES'),
    role_code VARCHAR(32) NOT NULL
        CHECK (role_code IN ('VIEWER', 'OPERATOR', 'MANAGER', 'ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, grant_scope)
);

CREATE TABLE IF NOT EXISTS business_legacy_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias TEXT NOT NULL,
    normalized_alias TEXT NOT NULL UNIQUE,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    source VARCHAR(64) NOT NULL DEFAULT 'legacy_messages',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    approved_by TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_business_legacy_aliases_business
    ON business_legacy_aliases(business_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS business_channel_bindings (
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    resolution_mode VARCHAR(64) NOT NULL
        CHECK (resolution_mode IN (
            'SINGLE_BUSINESS',
            'EXPLICIT_SELECTION',
            'VERIFIED_EXTERNAL_MAPPING',
            'VERIFIED_LOCATION_MAPPING'
        )),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_id, business_id)
);

CREATE INDEX IF NOT EXISTS idx_business_channel_bindings_business_channel
    ON business_channel_bindings(business_id, channel_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS business_locations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    location_name TEXT NOT NULL,
    normalized_location_name TEXT NOT NULL,
    location_type TEXT NOT NULL DEFAULT '',
    city TEXT NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'archived')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_id, normalized_location_name, location_type, city)
);

CREATE TABLE IF NOT EXISTS external_location_mappings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_location_id BIGINT NOT NULL REFERENCES business_locations(id) ON DELETE CASCADE,
    source_key VARCHAR(128) NOT NULL,
    external_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_key, external_id)
);

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS business_id BIGINT;
ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_business
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_tickets_business_id
    ON tickets(business_id)
    WHERE business_id IS NOT NULL;
