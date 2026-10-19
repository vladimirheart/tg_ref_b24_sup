-- References and fingerprints only; no raw dialogue/media/query is copied into audit.
CREATE TABLE ai_scoped_handoff_evidence (
    event_id BIGINT NOT NULL CHECK (event_id > 0),
    source_id BIGINT NOT NULL CHECK (source_id > 0),
    source_revision BIGINT NOT NULL CHECK (source_revision > 0),
    PRIMARY KEY(event_id,source_id)
);
CREATE TABLE ai_scoped_memory_evidence (
    source_id BIGINT PRIMARY KEY CHECK (source_id > 0),
    question_history_id BIGINT NOT NULL CHECK (question_history_id > 0),
    answer_history_id BIGINT NOT NULL CHECK (answer_history_id > question_history_id),
    question_fingerprint VARCHAR(64) NOT NULL CHECK (question_fingerprint ~ '^[0-9a-f]{64}$'),
    answer_fingerprint VARCHAR(64) NOT NULL CHECK (answer_fingerprint ~ '^[0-9a-f]{64}$')
);
-- No FK to mutable/deletable source/history rows: evidence remains a historical fact.
CREATE TRIGGER trg_scoped_handoff_evidence_immutable BEFORE UPDATE ON ai_scoped_handoff_evidence
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();
CREATE TRIGGER trg_scoped_memory_evidence_immutable BEFORE UPDATE ON ai_scoped_memory_evidence
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();
