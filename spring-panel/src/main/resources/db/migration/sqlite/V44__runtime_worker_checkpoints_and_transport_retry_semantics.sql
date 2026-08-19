ALTER TABLE integration_inbound_event_inbox
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE integration_inbound_event_inbox
    ADD COLUMN processing_started_at TEXT;

ALTER TABLE integration_inbound_event_inbox
    ADD COLUMN updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE integration_inbound_event_inbox
   SET attempt_count = CASE
           WHEN status = 'processed' THEN 1
           WHEN status IN ('failed', 'received', 'processing') THEN 1
           ELSE COALESCE(attempt_count, 0)
       END,
       processing_started_at = COALESCE(processing_started_at, received_at),
       updated_at = COALESCE(updated_at, processed_at, received_at, CURRENT_TIMESTAMP)
 WHERE attempt_count = 0
    OR processing_started_at IS NULL
    OR updated_at IS NULL;

CREATE TABLE IF NOT EXISTS runtime_worker_checkpoints (
    worker_key TEXT PRIMARY KEY,
    cursor_text TEXT,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
