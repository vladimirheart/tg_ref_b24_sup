package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class ChildPoolRoleLimitMigrationTest {
    private static final String PASSWORD = "a".repeat(64);

    @Test
    void raisesOnlyManagedOriginalQuotasAndPreservesIdentityAndFutureProvisioning() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("child_pool_quota");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,platform TEXT,is_active BOOLEAN)");
        for (String migration : List.of("V24__integration_transport_outbox.sql", "V85__outbound_delivery_receipts.sql",
                "V86__child_transport_principals.sql", "V94__automatic_child_infrastructure.sql")) {
            jdbc.execute(sql(migration));
        }
        long id = 312000 + Math.floorMod(System.nanoTime(), 1_000_000_000);
        for (long channel = id; channel < id + 3; channel++) {
            jdbc.update("INSERT INTO channels VALUES(?,'telegram',true)", channel);
            jdbc.queryForList("SELECT * FROM bot_provision_child_transport(?,'telegram',?,?)",
                channel, PASSWORD, ChildRabbitProvisionerTest.HASH);
        }
        jdbc.execute("ALTER ROLE iguana_child_" + (id + 1) + " CONNECTION LIMIT 32");
        jdbc.update("UPDATE bot_child_principals SET enabled=false WHERE channel_id=?", id + 2);
        var before = jdbc.queryForList("SELECT channel_id,role_oid,credential_digest,rabbit_password_hash FROM bot_child_infrastructure ORDER BY channel_id");
        jdbc.execute(sql("V103__child_pool_connection_quota.sql"));
        assertThat(jdbc.queryForList("SELECT channel_id,role_oid,credential_digest,rabbit_password_hash FROM bot_child_infrastructure ORDER BY channel_id")).isEqualTo(before);
        assertThat(quota(jdbc,id)).isEqualTo(20);
        assertThat(quota(jdbc,id+1)).isEqualTo(32);
        assertThat(quota(jdbc,id+2)).isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname=?",
            Boolean.class,"iguana_child_"+id)).isFalse();

        var connections = new ArrayList<Connection>();
        try (var owner = jdbc.getDataSource().getConnection()) {
            try {
                for (int i = 0; i < 20; i++) connections.add(DriverManager.getConnection(
                    owner.getMetaData().getURL(), "iguana_child_"+id, PASSWORD));
                assertThat(connections).hasSize(20);
                assertThatThrownBy(() -> DriverManager.getConnection(owner.getMetaData().getURL(), "iguana_child_"+id, PASSWORD))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("too many connections for role");
            } finally {
                for (var connection : connections) connection.close();
            }
        }
        // Optional diagnostics must not add PUBLIC relations to a restricted child.
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public");
        jdbc.execute(new org.springframework.core.io.FileSystemResource("../docker/postgres/20-query-diagnostics.sql")
            .getContentAsString(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(?,'public.pg_stat_statements','SELECT') OR "
            + "has_table_privilege(?,'public.pg_stat_statements_info','SELECT') OR "
            + "has_function_privilege(?,'public.pg_stat_statements(boolean)','EXECUTE')",
            Boolean.class,"iguana_child_"+id,"iguana_child_"+id,"iguana_child_"+id)).isFalse();
        long future = id+3;
        jdbc.update("INSERT INTO channels VALUES(?,'telegram',true)",future);
        jdbc.queryForList("SELECT * FROM bot_provision_child_transport(?,'telegram',?,?)",
            future,PASSWORD,ChildRabbitProvisionerTest.HASH);
        assertThat(quota(jdbc,future)).isEqualTo(20);
    }

    private int quota(JdbcTemplate jdbc,long channel) {
        return jdbc.queryForObject("SELECT rolconnlimit FROM pg_roles WHERE rolname=?",Integer.class,"iguana_child_"+channel);
    }

    private String sql(String migration) throws Exception {
        return new ClassPathResource("db/migration/postgresql/"+migration).getContentAsString(StandardCharsets.UTF_8);
    }
}
