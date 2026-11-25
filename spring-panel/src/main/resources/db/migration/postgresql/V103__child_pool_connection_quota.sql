-- Match the approved child JDBC maximum without changing identity, passwords or grants.
-- Retain the full provisioning function, its SECURITY DEFINER/search_path and ACL.
DO $migration$
DECLARE
    definition text;
    old_limit text := 'CONNECTION LIMIT 16';
    child record;
BEGIN
    definition := pg_get_functiondef(
        format('%I.bot_provision_child_transport(bigint,text,text,text)',current_schema())::regprocedure);
    IF (length(definition)-length(replace(definition,old_limit,''))) / length(old_limit) <> 1 THEN
        RAISE EXCEPTION 'Unexpected child provisioning function; review connection quota migration';
    END IF;
    EXECUTE replace(definition,old_limit,'CONNECTION LIMIT 20');

    -- Only known, active, automatically managed identities with the original quota.
    -- Changed OIDs, manual principals, elevated roles and custom quotas are untouched.
    FOR child IN
        SELECT r.rolname
        FROM bot_child_infrastructure journal
        JOIN bot_child_principals principal ON principal.channel_id=journal.channel_id
        JOIN channels channel ON channel.id=principal.channel_id
        JOIN pg_roles r ON r.oid=journal.role_oid AND r.rolname=principal.role_name
        WHERE principal.role_name='iguana_child_' || principal.channel_id::text
          AND principal.enabled AND channel.is_active
          AND principal.platform=journal.platform AND channel.platform=principal.platform
          AND r.rolconnlimit=16 AND r.rolcanlogin
          AND NOT (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls)
          AND NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member=r.oid)
        ORDER BY principal.channel_id
    LOOP
        EXECUTE format('ALTER ROLE %I CONNECTION LIMIT 20',child.rolname);
    END LOOP;
END
$migration$;
