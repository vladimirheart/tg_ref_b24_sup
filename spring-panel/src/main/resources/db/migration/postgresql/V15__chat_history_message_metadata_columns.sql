ALTER TABLE chat_history
    ADD COLUMN IF NOT EXISTS original_message TEXT;

ALTER TABLE chat_history
    ADD COLUMN IF NOT EXISTS forwarded_from TEXT;

ALTER TABLE chat_history
    ADD COLUMN IF NOT EXISTS file_name TEXT;
