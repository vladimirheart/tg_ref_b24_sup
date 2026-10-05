CREATE TABLE IF NOT EXISTS it_equipment_attribute_definitions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    equipment_type TEXT NOT NULL,
    attribute_key VARCHAR(100) NOT NULL,
    label TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    section_name TEXT,
    unit TEXT,
    help_text TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_it_equipment_attribute_value_type
        CHECK (value_type IN ('text', 'integer', 'decimal', 'boolean', 'enum')),
    CONSTRAINT ck_it_equipment_attribute_options_array
        CHECK (jsonb_typeof(options) = 'array'),
    CONSTRAINT uq_it_equipment_attribute_definition
        UNIQUE(equipment_type, attribute_key)
);

CREATE INDEX IF NOT EXISTS idx_it_equipment_attribute_definitions_type
    ON it_equipment_attribute_definitions(equipment_type, active, sort_order, id);

CREATE TABLE IF NOT EXISTS it_equipment_configuration_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    equipment_type TEXT NOT NULL,
    catalog_id BIGINT REFERENCES it_equipment_catalog(id) ON DELETE SET NULL,
    profile_name TEXT NOT NULL,
    description TEXT,
    values JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_it_equipment_configuration_profile_values_object
        CHECK (jsonb_typeof(values) = 'object'),
    CONSTRAINT uq_it_equipment_configuration_profile
        UNIQUE(equipment_type, profile_name)
);

CREATE INDEX IF NOT EXISTS idx_it_equipment_configuration_profiles_type
    ON it_equipment_configuration_profiles(equipment_type, active, profile_name, id);

CREATE INDEX IF NOT EXISTS idx_it_equipment_configuration_profiles_catalog
    ON it_equipment_configuration_profiles(catalog_id)
    WHERE catalog_id IS NOT NULL;
