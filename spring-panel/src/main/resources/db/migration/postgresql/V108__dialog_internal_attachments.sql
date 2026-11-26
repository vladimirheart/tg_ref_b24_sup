ALTER TABLE dialog_internal_messages DROP CONSTRAINT dialog_internal_messages_body_check;
ALTER TABLE dialog_internal_messages ADD CONSTRAINT dialog_internal_messages_body_check
    CHECK (char_length(body)<=10000 AND (body='' OR btrim(body)<>''));

CREATE TABLE dialog_internal_attachments (
    id UUID PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES dialog_internal_messages(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 9),
    original_name TEXT NOT NULL CHECK(char_length(original_name) BETWEEN 1 AND 240),
    mime_type TEXT NOT NULL,
    file_size BIGINT NOT NULL CHECK(file_size>0 AND file_size<=52428800),
    sha256 TEXT NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
    stored_key TEXT NOT NULL UNIQUE CHECK(stored_key ~ '^[a-f0-9]{32}/[a-f0-9-]{36}$'),
    UNIQUE(message_id,position)
);
CREATE TRIGGER preserve_dialog_internal_attachment BEFORE UPDATE OR DELETE ON dialog_internal_attachments
    FOR EACH ROW EXECUTE FUNCTION preserve_dialog_operator_record();
