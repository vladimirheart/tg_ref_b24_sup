\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN READ ONLY;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '3s';
WITH evidence AS (
    SELECT d.*, a.event_id IS NOT NULL AS receipt_present,
           a.provider_message_id IS NOT NULL AND a.accepted_at IS NOT NULL AS accepted,
           a.platform = :'platform'
           AND d.event_kind IS NOT DISTINCT FROM a.event_kind
           AND d.routing_key IS NOT DISTINCT FROM a.routing_key
           AND d.channel_id IS NOT DISTINCT FROM a.channel_id
           AND d.user_id IS NOT DISTINCT FROM a.user_id
           AND d.ticket_id IS NOT DISTINCT FROM a.ticket_id
           AND d.request_id IS NOT DISTINCT FROM a.request_id AS same_context,
           encode(sha256(convert_to(jsonb_build_object('delivery', to_jsonb(d), 'receipt', to_jsonb(a))::text, 'UTF8')), 'hex') AS snapshot
      FROM :"schema".integration_outbound_event_deliveries d
      LEFT JOIN :"schema".integration_outbound_delivery_receipts a ON a.event_id = d.event_id
     WHERE d.event_id = convert_from(decode(:'event_hex', 'hex'), 'UTF8')
       AND d.channel_id = :'channel_id'::bigint AND d.user_id = :'user_id'::bigint
       AND d.event_kind = 'feedback.prompt.dispatch'
       AND d.routing_key = 'integration.outbound.feedback.prompt.' || :'platform' || '.channel.' || :'channel_id'
)
SELECT COALESCE((SELECT jsonb_build_object(
    'EventId', event_id, 'ChannelId', channel_id::text, 'UserId', user_id::text,
    'Platform', :'platform', 'Status', status, 'ReceiptPresent', receipt_present,
    'PositiveReceipt', accepted, 'ContextMatches', COALESCE(same_context, false),
    'SnapshotSha256', snapshot,
    'Outcome', CASE WHEN status = 'delivered' THEN 'ALREADY_DELIVERED'
                    WHEN status IN ('sending', 'uncertain') AND accepted AND same_context THEN 'RECEIPT_AVAILABLE'
                    WHEN status = 'retryable' THEN 'RETRY_REQUIRES_CURRENT_AUTHORIZATION'
                    ELSE 'BLOCKED_REQUIRES_REVIEW' END) FROM evidence),
    jsonb_build_object('Outcome', 'NOT_FOUND'));
COMMIT;
