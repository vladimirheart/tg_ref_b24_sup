package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class UiEventOutboxScopeFlywayMigrationTest {
    @Test
    void upgradePreservesLegacyRowsAndProtectsOwnerSnapshotIncludingRawId() {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ui_outbox_upgrade_v61");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("60").load().migrate();
        jdbc.update("INSERT INTO ui_event_outbox(id, event_type, ticket_id, message_text, created_at) "
            + "VALUES (11, 'ticket_created', ' exact ', 'old payload', CURRENT_TIMESTAMP)");
        var before = jdbc.queryForList("SELECT * FROM ui_event_outbox");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(jdbc.queryForList("SELECT id, event_type, ticket_id, channel_id, message_text, message_type, attachment, rating, created_at "
            + "FROM ui_event_outbox")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox WHERE ticket_user_id IS NULL "
            + "AND ticket_public_id IS NULL AND ticket_scope_kind IS NULL AND ticket_business_id IS NULL", Long.class)).isEqualTo(1);
        jdbc.update("INSERT INTO ui_event_outbox(id, event_type, ticket_id, created_at, ticket_user_id, ticket_public_id, "
            + "ticket_scope_kind, ticket_business_id) VALUES (12, 'feedback_created', ' exact ', CURRENT_TIMESTAMP, 901, ?, 'BUSINESS', 7)",
            "a".repeat(32));
        for (String assignment : new String[]{"ticket_id = 'other'", "ticket_user_id = 902", "ticket_public_id = NULL",
            "ticket_scope_kind = 'GENERAL'", "ticket_business_id = 8",
            "ticket_user_id = NULL, ticket_public_id = NULL, ticket_scope_kind = NULL, ticket_business_id = NULL"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE ui_event_outbox SET " + assignment + " WHERE id = 12"))
                .hasMessageContaining("scope is immutable");
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE ui_event_outbox SET ticket_user_id = 901, ticket_scope_kind = 'UNRESOLVED' WHERE id = 11"))
            .hasMessageContaining("scope is immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE ui_event_outbox SET ticket_id = 'other' WHERE id = 11"))
            .hasMessageContaining("scope is immutable");
        for (String scope : new String[]{"NULL, NULL, 'BUSINESS', 7", "901, NULL, 'BUSINESS', NULL",
            "901, NULL, 'GENERAL', 7", "901, 'bad-id', 'UNRESOLVED', NULL", "901, NULL, 'UNKNOWN', NULL"}) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO ui_event_outbox(id, event_type, ticket_id, created_at, ticket_user_id, "
                + "ticket_public_id, ticket_scope_kind, ticket_business_id) VALUES (13, 'event', 'id', CURRENT_TIMESTAMP, " + scope + ")"))
                .hasMessageContaining("chk_ui_event_outbox_ticket_scope");
        }
        jdbc.update("DELETE FROM ui_event_outbox WHERE id = 12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isEqualTo(1);
    }
}
