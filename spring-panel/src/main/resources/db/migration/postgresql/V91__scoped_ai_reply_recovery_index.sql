-- Receipts are retained until manual deletion; recovery must scan only never-attempted work.
CREATE INDEX idx_ai_text_reply_queued ON ai_scoped_text_replies(incoming_event_id) WHERE state='QUEUED';
