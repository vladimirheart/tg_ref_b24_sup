-- V1 BUSINESS and general-only roots retain their exact previous representation.
-- V2 USER_SELECTION may commit a GENERAL catalog with a genuinely NULL business ID.
ALTER TABLE tickets DROP CONSTRAINT chk_tickets_business_questionnaire_selection;
ALTER TABLE tickets ADD CONSTRAINT chk_tickets_business_questionnaire_selection CHECK (
    (business_selection_source IS NULL AND questionnaire_catalog_sha256 IS NULL)
    OR (questionnaire_catalog_sha256 IS NOT NULL
        AND questionnaire_catalog_sha256 ~ '^[0-9a-f]{64}$'
        AND question_template_id IS NOT NULL AND question_template_sha256 IS NOT NULL
        AND ((business_selection_source IS NOT NULL AND business_selection_source='USER_SELECTION'
              AND NOT is_general AND business_id IS NOT NULL AND business_id>0 AND general_selection_source IS NULL)
            OR (business_selection_source IS NULL AND is_general AND business_id IS NULL
                AND general_selection_source IS NOT NULL AND general_selection_source='USER_SELECTION')))
);

CREATE FUNCTION prevent_ticket_shared_general_selection_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.questionnaire_catalog_sha256 IS NOT NULL OR NEW.questionnaire_catalog_sha256 IS NOT NULL)
       AND ROW(OLD.is_general,OLD.general_selection_source,OLD.business_id,OLD.user_id,OLD.ticket_id,OLD.channel_id)
           IS DISTINCT FROM ROW(NEW.is_general,NEW.general_selection_source,NEW.business_id,NEW.user_id,NEW.ticket_id,NEW.channel_id) THEN
        RAISE EXCEPTION 'Selected questionnaire owner and scope are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_ticket_shared_general_selection_immutable BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_shared_general_selection_change();
