-- Only new verified selections carry these fields; historical tickets stay untouched.
ALTER TABLE tickets
    ADD COLUMN business_selection_source VARCHAR(32),
    ADD COLUMN questionnaire_catalog_sha256 VARCHAR(64);

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_business_questionnaire_selection CHECK (
        (business_selection_source IS NULL AND questionnaire_catalog_sha256 IS NULL)
        OR (business_selection_source IS NOT NULL AND questionnaire_catalog_sha256 IS NOT NULL
            AND business_selection_source = 'USER_SELECTION'
            AND NOT is_general AND business_id IS NOT NULL AND business_id > 0
            AND general_selection_source IS NULL
            AND question_template_id IS NOT NULL AND question_template_sha256 IS NOT NULL
            AND questionnaire_catalog_sha256 ~ '^[0-9a-f]{64}$')
    );

CREATE FUNCTION prevent_ticket_business_selection_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.business_selection_source IS DISTINCT FROM NEW.business_selection_source
       OR OLD.questionnaire_catalog_sha256 IS DISTINCT FROM NEW.questionnaire_catalog_sha256 THEN
        RAISE EXCEPTION 'Ticket business selection provenance is immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tickets_business_selection_immutable
    BEFORE UPDATE OF business_selection_source, questionnaire_catalog_sha256 ON tickets
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_business_selection_change();
