-- No inference/backfill for historical tickets: their version stays NULL.
ALTER TABLE tickets ADD COLUMN questionnaire_version INTEGER;
ALTER TABLE tickets ADD CONSTRAINT ck_ticket_questionnaire_version CHECK (
    questionnaire_version IS NULL OR (
        questionnaire_version BETWEEN 1 AND 4 AND questionnaire_catalog_sha256 IS NOT NULL
        AND question_template_id IS NOT NULL AND question_template_sha256 IS NOT NULL
        AND ((is_general = TRUE AND business_id IS NULL AND general_selection_source IS NOT DISTINCT FROM 'USER_SELECTION')
          OR (is_general = FALSE AND business_id IS NOT NULL AND business_selection_source IS NOT DISTINCT FROM 'USER_SELECTION'))
    )
);
CREATE FUNCTION preserve_ticket_questionnaire_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.questionnaire_version IS DISTINCT FROM OLD.questionnaire_version THEN
        RAISE EXCEPTION 'Frozen questionnaire version cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ticket_questionnaire_version_immutable BEFORE UPDATE OF questionnaire_version ON tickets
FOR EACH ROW EXECUTE FUNCTION preserve_ticket_questionnaire_version();
