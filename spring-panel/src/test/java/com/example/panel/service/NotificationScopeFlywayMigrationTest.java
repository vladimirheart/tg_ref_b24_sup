package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationScopeFlywayMigrationTest {
    @Test
    void upgradePreservesUnclassifiedRowsAndRejectsSnapshotRewriteOrPartialScope() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("notification_scope_v60");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("59").load().migrate();
        jdbc.update("INSERT INTO notifications(user_identity, text, url) VALUES ('operator', 'historical', '/dialogs?ticketId=old')");
        var before = jdbc.queryForList("SELECT id, user_identity, text, url, is_read, created_at FROM notifications");

        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();

        assertThat(jdbc.queryForList("SELECT id, user_identity, text, url, is_read, created_at FROM notifications")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE ticket_user_id IS NULL AND ticket_id IS NULL "
            + "AND ticket_public_id IS NULL AND ticket_scope_kind IS NULL AND ticket_business_id IS NULL", Long.class)).isEqualTo(1);
        long id = jdbc.queryForObject("INSERT INTO notifications(user_identity, text, ticket_user_id, ticket_id, ticket_scope_kind, ticket_business_id) "
            + "VALUES ('operator', 'new', 901, ' exact ', 'BUSINESS', 7) RETURNING id", Long.class);
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE id = ?", id);
        for (String rewrite : new String[]{"ticket_user_id = 902", "ticket_id = 'other'",
            "ticket_public_id = '00000000000000000000000000000001'", "ticket_scope_kind = 'GENERAL'",
            "ticket_business_id = 8", "ticket_user_id = NULL, ticket_id = NULL, ticket_scope_kind = NULL, ticket_business_id = NULL"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE notifications SET " + rewrite + " WHERE id = ?", id))
                .hasMessageContaining("scope is immutable");
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE notifications SET ticket_user_id = 901, ticket_id = 'old', ticket_scope_kind = 'UNRESOLVED' "
            + "WHERE text = 'historical'")).hasMessageContaining("scope is immutable");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notifications(user_identity, text, ticket_id) VALUES ('operator', 'partial', 'old')"))
            .hasMessageContaining("chk_notification_ticket_scope");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notifications(user_identity, text, ticket_user_id, ticket_id, ticket_scope_kind) "
            + "VALUES ('operator', 'invalid business', 901, 'old', 'BUSINESS')"))
            .hasMessageContaining("chk_notification_ticket_scope");
    }
}
