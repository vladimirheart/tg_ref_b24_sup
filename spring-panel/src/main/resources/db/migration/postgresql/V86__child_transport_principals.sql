-- Credentials/LOGIN roles are provisioned separately by the database owner.
-- Child roles never own objects and receive no business-table grants.
CREATE TABLE bot_child_principals (
    role_name TEXT PRIMARY KEY CHECK (role_name ~ '^iguana_child_[1-9][0-9]{0,18}$'),
    channel_id BIGINT NOT NULL UNIQUE REFERENCES channels(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('telegram','vk','max')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    CHECK (role_name = 'iguana_child_' || channel_id::text AND channel_id > 0)
);
REVOKE ALL ON bot_child_principals FROM PUBLIC;

-- session_user cannot be changed by a non-superuser to another login.
-- SET ROLE, connection GUCs and payload/header channel IDs cannot choose a tenant.
DO $migration$
DECLARE namespace name := current_schema();
BEGIN
    EXECUTE format($definition$
        CREATE FUNCTION %I.bot_child_channel() RETURNS BIGINT
        LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = pg_catalog, %I
        AS $body$
            SELECT p.channel_id FROM %I.bot_child_principals p
              JOIN %I.channels c ON c.id=p.channel_id AND c.platform=p.platform
             WHERE p.role_name=session_user::text AND p.enabled AND c.is_active=true
        $body$
    $definition$, namespace, namespace, namespace, namespace);
    EXECUTE format($definition$
        CREATE FUNCTION %I.bot_child_platform() RETURNS TEXT
        LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = pg_catalog, %I
        AS $body$
            SELECT p.platform FROM %I.bot_child_principals p
              JOIN %I.channels c ON c.id=p.channel_id AND c.platform=p.platform
             WHERE p.role_name=session_user::text AND p.enabled AND c.is_active=true
        $body$
    $definition$, namespace, namespace, namespace, namespace);
END $migration$;
CREATE FUNCTION bot_child_bound_channel() RETURNS BIGINT
LANGUAGE SQL STABLE SET search_path = pg_catalog
AS $$ SELECT CASE WHEN session_user::text ~ '^iguana_child_[1-9][0-9]{0,18}$'
    THEN substring(session_user::text FROM 14)::bigint ELSE NULL END $$;
REVOKE ALL ON FUNCTION bot_child_channel(), bot_child_platform(), bot_child_bound_channel() FROM PUBLIC;

ALTER TABLE integration_transport_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_transport_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY transport_existing_access ON integration_transport_outbox USING (true) WITH CHECK (true);
CREATE POLICY transport_child_scope ON integration_transport_outbox AS RESTRICTIVE
    USING (session_user::text !~ '^iguana_child_[1-9][0-9]*$'
        OR (channel_id=bot_child_channel() AND transport_source IN ('bot.inbound.client-message','bot.inbound.ticket-created')))
    WITH CHECK (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR (
        channel_id=bot_child_channel()
        AND transport_source IN ('bot.inbound.client-message','bot.inbound.ticket-created')
        AND payload_json::jsonb->'channelId'=to_jsonb(channel_id)
        AND payload_json::jsonb->>'platform'=bot_child_platform()
        AND payload_json::jsonb->>'eventId'=event_id
        AND payload_json::jsonb->>'eventKind'=event_kind
        AND payload_json::jsonb->>'ticketId'=ticket_id
        AND payload_json::jsonb->'userId'=to_jsonb(user_id)
    ));

ALTER TABLE integration_outbound_event_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_outbound_event_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY deliveries_existing_access ON integration_outbound_event_deliveries USING (true) WITH CHECK (true);
CREATE POLICY deliveries_child_scope ON integration_outbound_event_deliveries AS RESTRICTIVE
    USING (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR channel_id=bot_child_bound_channel())
    WITH CHECK (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR
        (channel_id=bot_child_bound_channel() AND (channel_id=bot_child_channel() OR
            (status='delivered' AND EXISTS (
                SELECT 1 FROM integration_outbound_delivery_receipts r
                 WHERE r.event_id=integration_outbound_event_deliveries.event_id
                   AND r.channel_id=integration_outbound_event_deliveries.channel_id
                   AND r.user_id IS NOT DISTINCT FROM integration_outbound_event_deliveries.user_id
                   AND r.ticket_id IS NOT DISTINCT FROM integration_outbound_event_deliveries.ticket_id
                   AND r.request_id IS NOT DISTINCT FROM integration_outbound_event_deliveries.request_id
                   AND r.event_kind IS NOT DISTINCT FROM integration_outbound_event_deliveries.event_kind
                   AND r.routing_key IS NOT DISTINCT FROM integration_outbound_event_deliveries.routing_key
                   AND r.accepted_at IS NOT NULL AND r.provider_message_id IS NOT NULL
            )))));

ALTER TABLE integration_outbound_delivery_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_outbound_delivery_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY receipts_existing_access ON integration_outbound_delivery_receipts USING (true) WITH CHECK (true);
CREATE POLICY receipts_child_scope ON integration_outbound_delivery_receipts AS RESTRICTIVE FOR SELECT
    USING (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR channel_id=bot_child_bound_channel());
CREATE POLICY receipts_child_prepare ON integration_outbound_delivery_receipts AS RESTRICTIVE FOR INSERT
    WITH CHECK (session_user::text !~ '^iguana_child_[1-9][0-9]*$'
        OR (channel_id=bot_child_channel() AND platform=bot_child_platform()));
-- Only provider result columns are granted for UPDATE. Context stays immutable.
-- Preserve an already-started result even after channel disable/rebind/revoke.
CREATE POLICY receipts_child_result ON integration_outbound_delivery_receipts AS RESTRICTIVE FOR UPDATE
    USING (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR channel_id=bot_child_bound_channel())
    WITH CHECK (session_user::text !~ '^iguana_child_[1-9][0-9]*$' OR channel_id=bot_child_bound_channel());
