-- Sender role is not enough to render the historical operator identity.
-- This stays nullable so legacy rows preserve their original provenance.
ALTER TABLE chat_history
    ADD COLUMN IF NOT EXISTS sender_identity TEXT;
