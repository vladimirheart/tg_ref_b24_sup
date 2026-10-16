-- NULL business_id alone is legacy/unresolved. Only an explicit GENERAL choice
-- may create a new business-free ticket in the strict write path.
ALTER TABLE tickets
    ADD COLUMN is_general BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tickets
    ADD COLUMN general_selection_source VARCHAR(32);

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_general_scope
    CHECK (
        (is_general = FALSE AND general_selection_source IS NULL)
        OR (is_general = TRUE AND business_id IS NULL
            AND general_selection_source IS NOT NULL
            AND general_selection_source IN ('USER_SELECTION', 'GENERAL_CHANNEL'))
    );
