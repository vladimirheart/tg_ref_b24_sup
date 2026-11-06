-- Nullable metadata: existing history/transport identities are unchanged. No historical backfill.
ALTER TABLE chat_history ADD COLUMN source_link_json TEXT;
ALTER TABLE chat_history ADD CONSTRAINT chat_history_source_link_shape CHECK (
    source_link_json IS NULL OR (length(source_link_json) <= 4096
        AND jsonb_typeof(source_link_json::jsonb) = 'object'
        AND source_link_json::jsonb->>'type' IN ('forward','reply')
        AND jsonb_typeof(source_link_json::jsonb->'providerMessageId') = 'string'
        AND length(source_link_json::jsonb->>'providerMessageId') BETWEEN 1 AND 512) IS TRUE
);
