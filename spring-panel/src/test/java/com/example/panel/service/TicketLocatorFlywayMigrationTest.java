package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TicketLocatorFlywayMigrationTest {

    @Test
    void canonicalPostgresqlMigrationChainAppliesV52WithImmutablePublicLocator() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ticket_locator_flyway_v52");
        Flyway flyway = Flyway.configure()
                .dataSource(jdbc.getDataSource())
                .locations("filesystem:src/main/resources/db/migration/postgresql")
                .load();

        flyway.migrate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '52' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = 'tickets'
                   AND column_name = 'ticket_public_id'
                """, String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM pg_trigger trigger
                  JOIN pg_class table_info ON table_info.oid = trigger.tgrelid
                  JOIN pg_namespace schema_info ON schema_info.oid = table_info.relnamespace
                 WHERE trigger.tgname = 'trg_tickets_ticket_public_id_immutable'
                   AND schema_info.nspname = current_schema()
                """, Integer.class)).isEqualTo(1);
    }
}
