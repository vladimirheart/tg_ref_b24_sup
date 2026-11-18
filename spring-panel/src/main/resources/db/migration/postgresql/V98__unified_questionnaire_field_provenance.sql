-- A unified questionnaire verifies its template directly and intentionally has no shared catalog hash.
-- Preserve the older USER_SELECTION and GENERAL contracts while admitting this separate provenance.
ALTER TABLE tickets DROP CONSTRAINT chk_tickets_business_questionnaire_selection;

ALTER TABLE tickets ADD CONSTRAINT chk_tickets_business_questionnaire_selection CHECK (
    (business_selection_source IS NULL AND questionnaire_catalog_sha256 IS NULL)
    OR (business_selection_source = 'QUESTIONNAIRE_FIELD'
        AND questionnaire_catalog_sha256 IS NULL
        AND NOT is_general
        AND business_id IS NOT NULL AND business_id > 0
        AND general_selection_source IS NULL
        AND question_template_id IS NOT NULL
        AND question_template_sha256 ~ '^[0-9a-f]{64}$')
    OR (questionnaire_catalog_sha256 IS NOT NULL
        AND questionnaire_catalog_sha256 ~ '^[0-9a-f]{64}$'
        AND question_template_id IS NOT NULL
        AND question_template_sha256 IS NOT NULL
        AND ((business_selection_source = 'USER_SELECTION'
              AND NOT is_general AND business_id IS NOT NULL AND business_id > 0
              AND general_selection_source IS NULL)
            OR (business_selection_source IS NULL AND is_general AND business_id IS NULL
                AND general_selection_source = 'USER_SELECTION')))
);
