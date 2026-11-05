\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '3s';
SET LOCAL idle_in_transaction_session_timeout = '15s';
-- Lock only the requested delivery and receipt. Never lock a whole table or touch broker state.
\o :sink
SELECT d.event_id FROM :"schema".integration_outbound_event_deliveries d
 WHERE d.event_id = convert_from(decode(:'event_hex', 'hex'), 'UTF8')
   AND d.channel_id = :'channel_id'::bigint AND d.user_id = :'user_id'::bigint FOR UPDATE;
SELECT a.event_id FROM :"schema".integration_outbound_delivery_receipts a
 WHERE a.event_id = convert_from(decode(:'event_hex', 'hex'), 'UTF8')
   AND a.channel_id = :'channel_id'::bigint AND a.user_id = :'user_id'::bigint FOR UPDATE;
\o
WITH reconciled AS (
    UPDATE :"schema".integration_outbound_event_deliveries d
       SET status = 'delivered', delivered_at = a.accepted_at,
           last_error = NULL, updated_at = (clock_timestamp() AT TIME ZONE 'UTC')
      FROM :"schema".integration_outbound_delivery_receipts a
     WHERE d.event_id = a.event_id
       AND d.event_id = convert_from(decode(:'event_hex', 'hex'), 'UTF8')
       AND d.channel_id = :'channel_id'::bigint AND d.user_id = :'user_id'::bigint
       AND d.status IN ('sending', 'uncertain')
       AND d.event_kind = 'feedback.prompt.dispatch'
       AND d.routing_key = 'integration.outbound.feedback.prompt.' || :'platform' || '.channel.' || :'channel_id'
       AND a.platform = :'platform' AND a.provider_message_id IS NOT NULL AND a.accepted_at IS NOT NULL
       AND d.event_kind IS NOT DISTINCT FROM a.event_kind
       AND d.routing_key IS NOT DISTINCT FROM a.routing_key
       AND d.channel_id IS NOT DISTINCT FROM a.channel_id
       AND d.user_id IS NOT DISTINCT FROM a.user_id
       AND d.ticket_id IS NOT DISTINCT FROM a.ticket_id
       AND d.request_id IS NOT DISTINCT FROM a.request_id
       AND encode(sha256(convert_to(jsonb_build_object('delivery', to_jsonb(d), 'receipt', to_jsonb(a))::text, 'UTF8')), 'hex') = :'snapshot'
    RETURNING d.event_id
)
SELECT jsonb_build_object('Reconciled', EXISTS(SELECT 1 FROM reconciled),
    'Outcome', CASE WHEN EXISTS(SELECT 1 FROM reconciled) THEN 'DELIVERED_FROM_RECEIPT'
                    ELSE 'SNAPSHOT_OR_RECEIPT_REJECTED' END);
COMMIT;
