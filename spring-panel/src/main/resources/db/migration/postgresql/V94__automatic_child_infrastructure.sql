-- Only this backend-owned function may create a restricted child SQL login.
-- No passwords are stored in the journal; the broker hash also makes retries
-- after an ambiguous HTTP result verifiable without rotating an existing user.
CREATE TABLE bot_child_infrastructure (
    channel_id BIGINT PRIMARY KEY REFERENCES channels(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('telegram','vk','max')),
    role_oid OID NOT NULL,
    credential_digest TEXT NOT NULL,
    rabbit_password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at TIMESTAMPTZ
);
REVOKE ALL ON bot_child_infrastructure FROM PUBLIC;

CREATE FUNCTION bot_provision_child_transport(p_channel BIGINT, p_platform TEXT,
    p_password TEXT, p_rabbit_hash TEXT)
RETURNS TABLE(managed BOOLEAN, password_hash TEXT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $body$
DECLARE
    namespace name := (current_schemas(false))[2];
    child_role text := 'iguana_child_' || p_channel::text;
    principal record;
    journal record;
    actual_role record;
BEGIN
    IF p_channel IS NULL OR p_channel <= 0 OR p_platform IS NULL
        OR p_platform NOT IN ('telegram','vk','max') OR p_password IS NULL
        OR p_password !~ '^[a-f0-9]{64}$' OR p_rabbit_hash IS NULL
        OR octet_length(decode(p_rabbit_hash,'base64')) <> 36 THEN
        RAISE EXCEPTION 'Invalid child infrastructure input';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('iguana-child-infrastructure:' || p_channel,0));
    PERFORM 1 FROM channels WHERE id=p_channel AND platform=p_platform AND is_active=true FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Exact active canonical channel required'; END IF;
    SELECT * INTO principal FROM bot_child_principals WHERE channel_id=p_channel FOR SHARE;
    SELECT * INTO journal FROM bot_child_infrastructure WHERE channel_id=p_channel;
    SELECT * INTO actual_role FROM pg_roles WHERE rolname=child_role;

    IF principal.channel_id IS NOT NULL AND journal.channel_id IS NULL THEN
        IF principal.role_name<>child_role OR principal.platform<>p_platform OR NOT principal.enabled
            OR actual_role.oid IS NULL THEN RAISE EXCEPTION 'Existing child binding is unavailable'; END IF;
        -- Existing manual provisioning is not adopted, rotated or repaired.
        RETURN QUERY SELECT false,NULL::text;
        RETURN;
    END IF;
    IF journal.channel_id IS NOT NULL THEN
        IF principal.channel_id IS NULL OR principal.platform<>p_platform OR NOT principal.enabled
            OR principal.role_name<>child_role OR journal.platform<>p_platform
            OR journal.credential_digest<>md5(p_password) OR actual_role.oid IS NULL
            OR actual_role.oid<>journal.role_oid OR NOT actual_role.rolcanlogin
            OR actual_role.rolsuper OR actual_role.rolcreatedb OR actual_role.rolcreaterole
            OR actual_role.rolreplication OR actual_role.rolbypassrls
            OR EXISTS(SELECT 1 FROM pg_auth_members WHERE member=actual_role.oid) THEN
            RAISE EXCEPTION 'Managed child identity changed; administrative review required';
        END IF;
        RETURN QUERY SELECT true,journal.rabbit_password_hash::text;
        RETURN;
    END IF;
    IF actual_role.oid IS NOT NULL THEN RAISE EXCEPTION 'Unowned child role already exists'; END IF;
    IF EXISTS (SELECT 1 FROM pg_namespace n,
          LATERAL aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a
          WHERE n.nspname=namespace AND a.grantee=0 AND a.privilege_type='CREATE')
        OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace,
          LATERAL aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) a
          WHERE n.nspname=namespace AND c.relkind IN ('r','p','v','m','f') AND a.grantee=0) THEN
        RAISE EXCEPTION 'PUBLIC grants must be reviewed before provisioning children';
    END IF;
    EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 16',child_role,p_password);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I',current_database(),child_role);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I',namespace,child_role);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %1$I.bot_child_channel(), %1$I.bot_child_platform(), %1$I.bot_child_bound_channel() TO %2$I',namespace,child_role);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON %1$I.integration_transport_outbox, %1$I.integration_outbound_event_deliveries TO %2$I',namespace,child_role);
    EXECUTE format('GRANT SELECT,INSERT ON %I.integration_outbound_delivery_receipts TO %I',namespace,child_role);
    EXECUTE format('GRANT UPDATE(provider_message_id,accepted_at) ON %I.integration_outbound_delivery_receipts TO %I',namespace,child_role);
    EXECUTE format('ALTER ROLE %I SET search_path TO pg_catalog,%I',child_role,namespace);
    INSERT INTO bot_child_principals(role_name,channel_id,platform) VALUES(child_role,p_channel,p_platform);
    INSERT INTO bot_child_infrastructure(channel_id,platform,role_oid,credential_digest,rabbit_password_hash)
        SELECT p_channel,p_platform,oid,md5(p_password),p_rabbit_hash FROM pg_roles WHERE rolname=child_role;
    RETURN QUERY SELECT true,p_rabbit_hash;
END
$body$;
REVOKE ALL ON FUNCTION bot_provision_child_transport(BIGINT,TEXT,TEXT,TEXT) FROM PUBLIC;
DO $migration$
BEGIN
    EXECUTE format('ALTER FUNCTION %I.bot_provision_child_transport(BIGINT,TEXT,TEXT,TEXT) SET search_path TO pg_catalog,%I,pg_temp',current_schema(),current_schema());
END
$migration$;
