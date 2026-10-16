-- Nullable for historical tickets; never infer a questionnaire from today's config.
ALTER TABLE tickets
    ADD COLUMN question_template_id TEXT,
    ADD COLUMN question_template_sha256 VARCHAR(64);

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_questionnaire_provenance CHECK (
        (question_template_id IS NULL AND question_template_sha256 IS NULL)
        OR (question_template_id IS NOT NULL AND question_template_sha256 IS NOT NULL
            AND question_template_id <> '' AND question_template_id = btrim(question_template_id)
            AND question_template_sha256 ~ '^[0-9a-f]{64}$')
    );

CREATE FUNCTION prevent_ticket_questionnaire_provenance_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.question_template_id IS DISTINCT FROM NEW.question_template_id
       OR OLD.question_template_sha256 IS DISTINCT FROM NEW.question_template_sha256 THEN
        RAISE EXCEPTION 'Ticket questionnaire provenance is immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tickets_questionnaire_provenance_immutable
    BEFORE UPDATE OF question_template_id, question_template_sha256 ON tickets
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_questionnaire_provenance_change();
