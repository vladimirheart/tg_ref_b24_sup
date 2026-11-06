-- psql variables: channel_id, platform, password. Run as DB owner, not application role.
-- Password must be supplied over stdin/private input; never put it on the command line.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE child_provision_input (channel_id bigint, platform text, password text) ON COMMIT DROP;
INSERT INTO child_provision_input VALUES (:'channel_id'::bigint, :'platform', :'password');
DO $$
DECLARE
    spec record;
    child_role text;
BEGIN
    SELECT * INTO STRICT spec FROM pg_temp.child_provision_input;
    IF spec.channel_id <= 0 OR spec.platform NOT IN ('telegram','vk','max')
        OR spec.password !~ '^[0-9a-f]{64}$' OR spec.password IS NULL THEN
        RAISE EXCEPTION 'Invalid child principal input';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.channels WHERE id=spec.channel_id AND platform=spec.platform AND is_active=true FOR SHARE) THEN
        RAISE EXCEPTION 'Exact active canonical channel is required';
    END IF;
    child_role := 'iguana_child_' || spec.channel_id::text;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=child_role) THEN
        -- Never silently adopt or rotate an existing role. Provision is an explicit one-time operation.
        RAISE EXCEPTION 'Child role already exists; inspect its binding and grants before a separate rotation';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_namespace n, LATERAL aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a
               WHERE n.nspname='public' AND a.grantee=0 AND a.privilege_type='CREATE')
        OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace,
                   LATERAL aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) a
                   WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f') AND a.grantee=0) THEN
        RAISE EXCEPTION 'PUBLIC grants must be reviewed before provisioning restricted children';
    END IF;
    EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 16', child_role, spec.password);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), child_role);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', child_role);
    EXECUTE format('GRANT EXECUTE ON FUNCTION public.bot_child_channel(), public.bot_child_platform(), public.bot_child_bound_channel() TO %I', child_role);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON public.integration_transport_outbox, public.integration_outbound_event_deliveries TO %I', child_role);
    EXECUTE format('GRANT SELECT,INSERT ON public.integration_outbound_delivery_receipts TO %I', child_role);
    EXECUTE format('GRANT UPDATE(provider_message_id,accepted_at) ON public.integration_outbound_delivery_receipts TO %I', child_role);
    EXECUTE format('ALTER ROLE %I SET search_path TO pg_catalog,public', child_role);
    INSERT INTO public.bot_child_principals(role_name,channel_id,platform) VALUES(child_role,spec.channel_id,spec.platform);
END $$;
COMMIT;
