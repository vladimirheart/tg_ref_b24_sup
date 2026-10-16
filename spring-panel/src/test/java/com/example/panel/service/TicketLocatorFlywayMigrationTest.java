package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketLocatorFlywayMigrationTest {

    @Test
    void questionnaireProvenanceUpgradePreservesHistoryAndFreezesNewVersions() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ticket_questionnaire_v58");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("57").load().migrate();
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (901, 'old-unresolved')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source) "
            + "VALUES (902, 'old-general', TRUE, 'GENERAL_CHANNEL')");

        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history "
            + "WHERE version = '58' AND success", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE question_template_id IS NULL "
            + "AND question_template_sha256 IS NULL", Integer.class)).isEqualTo(2);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source, "
            + "question_template_id, question_template_sha256) VALUES (903, 'new-general', TRUE, "
            + "'GENERAL_CHANNEL', 'q-general', ?)", "a".repeat(64));
        jdbc.update("UPDATE tickets SET status = 'resolved' WHERE ticket_id = 'new-general'");
        jdbc.update("UPDATE tickets SET question_template_id = question_template_id "
            + "WHERE ticket_id = 'new-general'");
        for (String sql : new String[] {
            "UPDATE tickets SET question_template_id = 'other' WHERE ticket_id = 'new-general'",
            "UPDATE tickets SET question_template_sha256 = repeat('b', 64) WHERE ticket_id = 'new-general'",
            "UPDATE tickets SET question_template_id = NULL, question_template_sha256 = NULL "
                + "WHERE ticket_id = 'new-general'",
            "UPDATE tickets SET question_template_id = 'q', question_template_sha256 = repeat('a', 64) "
                + "WHERE ticket_id = 'old-general'"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).hasMessageContaining("provenance is immutable");
        }
        for (Object[] pair : new Object[][] {
            {"q", null}, {null, "a".repeat(64)}, {"", "a".repeat(64)}, {" q ", "a".repeat(64)},
            {"q", "bad"}, {"q", "A".repeat(64)}
        }) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO tickets(user_id, ticket_id, "
                + "question_template_id, question_template_sha256) VALUES (904, 'invalid', ?, ?)", pair))
                .hasMessageContaining("chk_tickets_questionnaire_provenance");
        }
        assertThat(jdbc.queryForObject("SELECT question_template_sha256 FROM tickets "
            + "WHERE ticket_id = 'new-general'", String.class)).isEqualTo("a".repeat(64));
    }

    @Test
    void canonicalPostgresqlMigrationChainAppliesPublicLocatorAndManualClients() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ticket_locator_flyway_v54");
        Flyway flyway = Flyway.configure()
                .dataSource(jdbc.getDataSource())
                .locations("filesystem:src/main/resources/db/migration/postgresql")
                .load();

        flyway.migrate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '52' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '54' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '56' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '57' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (901, 'legacy-unresolved')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source) "
                + "VALUES (902, 'general-selected', TRUE, 'USER_SELECTION')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source) "
                + "VALUES (903, 'general-channel', TRUE, 'GENERAL_CHANNEL')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general) "
                + "VALUES (904, 'not-selected', TRUE)"))
                .hasMessageContaining("chk_tickets_general_scope");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                        + "AND table_name = 'manual_business_clients'",
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
