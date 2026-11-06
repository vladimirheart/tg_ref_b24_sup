package com.example.supportbot.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;

/** Refuses a privileged/foreign datasource before repositories, schedulers or providers start. */
public final class ChildTransportDatabase {
    private ChildTransportDatabase() {}

    public static void validate(DataSource source, Environment environment) {
        if (!"rabbitmq".equals(environment.getProperty("app.integration.transport.mode", "jdbc"))) return;
        String token = environment.getProperty("app.integration.panel-api.token", "");
        String platform = environment.getProperty("app.bot-runtime.platform", "");
        long channel = environment.getProperty("app.bot-runtime.channel-id", Long.class, 0L);
        if (!token.matches("iguana-child-v1:(telegram|vk|max):[1-9][0-9]{0,18}:[0-9a-f]{64}")
                || !token.startsWith("iguana-child-v1:" + platform + ":" + channel + ":") || channel <= 0) {
            throw new IllegalStateException("Rabbit bot requires a channel-bound API principal");
        }
        try (Connection connection = source.getConnection(); PreparedStatement query = connection.prepareStatement("""
            SELECT session_user::text=?, public.bot_child_channel() IS NOT DISTINCT FROM ?::bigint,
                   public.bot_child_platform() IS NOT DISTINCT FROM ?::text,
                   NOT(r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls),
                   NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=r.oid),
                   NOT EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                     WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%%'
                       AND t.relkind IN ('r','p','v','m','S','f')
                       AND (t.relowner=r.oid OR (t.relkind='S' AND has_sequence_privilege(r.oid,t.oid,'SELECT,USAGE,UPDATE'))
                         OR (t.relkind <> 'S' AND (has_table_privilege(r.oid,t.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                           OR has_any_column_privilege(r.oid,t.oid,'SELECT,INSERT,UPDATE,REFERENCES'))
                         AND NOT(n.nspname='public' AND t.relname IN ('integration_transport_outbox','integration_outbound_event_deliveries','integration_outbound_delivery_receipts'))))),
                   NOT EXISTS(SELECT 1 FROM pg_namespace n WHERE has_schema_privilege(r.oid,n.oid,'CREATE')),
                   NOT EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                     WHERE n.nspname='public' AND t.relname IN ('integration_transport_outbox','integration_outbound_event_deliveries','integration_outbound_delivery_receipts')
                       AND NOT(t.relrowsecurity AND t.relforcerowsecurity)),
                   NOT EXISTS(SELECT 1 FROM pg_proc f JOIN pg_namespace n ON n.oid=f.pronamespace
                     WHERE f.prosecdef AND n.nspname NOT IN ('pg_catalog','information_schema')
                       AND has_function_privilege(r.oid,f.oid,'EXECUTE')
                       AND NOT(n.nspname='public' AND f.proname IN ('bot_child_channel','bot_child_platform'))),
                   NOT EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                     WHERE n.nspname='public' AND t.relname IN ('integration_transport_outbox','integration_outbound_event_deliveries','integration_outbound_delivery_receipts')
                       AND (has_table_privilege(r.oid,t.oid,'DELETE,TRUNCATE,REFERENCES,TRIGGER')
                         OR has_any_column_privilege(r.oid,t.oid,'REFERENCES')
                         OR (t.relname='integration_outbound_delivery_receipts' AND EXISTS(
                           SELECT 1 FROM pg_attribute a WHERE a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                             AND a.attname NOT IN ('provider_message_id','accepted_at') AND has_column_privilege(r.oid,t.oid,a.attnum,'UPDATE')))))
              FROM pg_roles r WHERE r.rolname=session_user
            """)) {
            query.setString(1, "iguana_child_" + channel); query.setLong(2, channel); query.setString(3, platform);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Child database identity is unavailable");
                for (int i = 1; i <= 10; i++) if (!result.getBoolean(i) || result.wasNull()) {
                    throw new IllegalStateException("Unsafe child database principal (check " + i + ")");
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("Child transport schema/principal V86 is required", ex); }
    }

    public static boolean isRestricted(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT session_user::text ~ '^iguana_child_[1-9][0-9]*$'", Boolean.class));
    }
}
