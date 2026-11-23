CREATE TABLE IF NOT EXISTS it_equipment_hierarchy_branches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    node_kind VARCHAR(16) NOT NULL,
    equipment_type TEXT NOT NULL,
    equipment_vendor TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_it_equipment_hierarchy_branch_kind CHECK (node_kind IN ('type', 'vendor')),
    CONSTRAINT ck_it_equipment_hierarchy_branch_path CHECK (
        (node_kind = 'type' AND equipment_vendor = '')
        OR (node_kind = 'vendor' AND equipment_vendor <> '')
    ),
    CONSTRAINT uq_it_equipment_hierarchy_branch UNIQUE(node_kind, equipment_type, equipment_vendor)
);

CREATE INDEX IF NOT EXISTS idx_it_equipment_hierarchy_branch_path
    ON it_equipment_hierarchy_branches(equipment_type, equipment_vendor);

CREATE TABLE IF NOT EXISTS it_equipment_catalog_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_vendor TEXT NOT NULL DEFAULT '',
    source_model TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_vendor TEXT NOT NULL DEFAULT '',
    target_model TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_it_equipment_catalog_alias_source UNIQUE(source_type, source_vendor, source_model)
);

CREATE INDEX IF NOT EXISTS idx_it_equipment_catalog_alias_target
    ON it_equipment_catalog_aliases(target_type, target_vendor, target_model);
