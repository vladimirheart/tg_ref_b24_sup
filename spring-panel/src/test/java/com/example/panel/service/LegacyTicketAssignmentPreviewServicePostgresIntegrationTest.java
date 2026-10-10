package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LegacyTicketAssignmentPreviewServicePostgresIntegrationTest {

    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql"
    );

    @Test
    void previewsOnlyExactApprovedAliasesAndNeverWritesTickets() throws Exception {
        JdbcTemplate jdbc = freshSchema("legacy_ticket_assignment_preview");
        applyFoundationMigration(jdbc);

        Long sushiId = businessId(jdbc, "sushi");
        Long blinyId = businessId(jdbc, "bliny");
        jdbc.update("""
                INSERT INTO business_legacy_aliases(alias, normalized_alias, business_id, approved_by)
                VALUES ('Legacy Sushi', 'legacy sushi', ?, 'approved-test')
                """, sushiId);
        jdbc.update("""
                INSERT INTO business_legacy_aliases(alias, normalized_alias, business_id, approved_by)
                VALUES
                    ('Ambiguous', 'ambiguous-sushi', ?, 'approved-test'),
                    ('Ambiguous', 'ambiguous-bliny', ?, 'approved-test')
                """, sushiId, blinyId);

        insertTicketAndRootMessage(jdbc, 1L, "ready", 101L, "Legacy Sushi");
        insertTicketAndRootMessage(jdbc, 1L, "blank", 102L, null);
        insertTicketAndRootMessage(jdbc, 1L, "unknown", 103L, "Not approved");
        insertTicketAndRootMessage(jdbc, 1L, "exact-only", 104L, "legacy sushi");
        insertTicketAndRootMessage(jdbc, 1L, "already", 105L, "Legacy Sushi");
        jdbc.update("UPDATE tickets SET business_id = ? WHERE user_id = 1 AND ticket_id = 'already'", sushiId);
        insertTicketAndRootMessage(jdbc, 1L, "conflict", 106L, "Legacy Sushi");
        jdbc.update("UPDATE tickets SET business_id = ? WHERE user_id = 1 AND ticket_id = 'conflict'", blinyId);
        insertTicketAndRootMessage(jdbc, 1L, "ambiguous", 107L, "Ambiguous");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, group_msg_id) VALUES (1, 'missing-root', 108)");

        LegacyTicketAssignmentPreviewService service = new LegacyTicketAssignmentPreviewService(jdbc);

        assertThat(service.preview()).containsExactly(
                preview("already", sushiId, "Legacy Sushi", LegacyTicketAssignmentPreviewService.AssignmentState.ALREADY_ASSIGNED, sushiId),
                preview("ambiguous", null, "Ambiguous", LegacyTicketAssignmentPreviewService.AssignmentState.AMBIGUOUS_MAPPING, null),
                preview("blank", null, null, LegacyTicketAssignmentPreviewService.AssignmentState.UNRESOLVED, null),
                preview("conflict", blinyId, "Legacy Sushi", LegacyTicketAssignmentPreviewService.AssignmentState.CONFLICT, sushiId),
                preview("exact-only", null, "legacy sushi", LegacyTicketAssignmentPreviewService.AssignmentState.UNRESOLVED, null),
                preview("missing-root", null, null, LegacyTicketAssignmentPreviewService.AssignmentState.UNRESOLVED, null),
                preview("ready", null, "Legacy Sushi", LegacyTicketAssignmentPreviewService.AssignmentState.READY_TO_ASSIGN, sushiId),
                preview("unknown", null, "Not approved", LegacyTicketAssignmentPreviewService.AssignmentState.UNRESOLVED, null)
        );
        assertThat(jdbc.queryForList(
                "SELECT ticket_id, business_id FROM tickets WHERE user_id = 1 ORDER BY ticket_id"
        )).extracting(row -> row.get("ticket_id"), row -> row.get("business_id")).containsExactly(
                tuple("already", sushiId),
                tuple("ambiguous", null),
                tuple("blank", null),
                tuple("conflict", blinyId),
                tuple("exact-only", null),
                tuple("missing-root", null),
                tuple("ready", null),
                tuple("unknown", null)
        );
    }

    private LegacyTicketAssignmentPreviewService.AssignmentPreview preview(
            String ticketId,
            Long currentBusinessId,
            String legacyAlias,
            LegacyTicketAssignmentPreviewService.AssignmentState state,
            Long proposedBusinessId
    ) {
        return new LegacyTicketAssignmentPreviewService.AssignmentPreview(
                1L,
                ticketId,
                currentBusinessId,
                legacyAlias,
                state,
                proposedBusinessId
        );
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    group_msg_id BIGINT,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE messages (
                    id BIGINT PRIMARY KEY,
                    ticket_id TEXT NOT NULL,
                    business TEXT
                )
                """);
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'operator')");
        return jdbc;
    }

    private void insertTicketAndRootMessage(JdbcTemplate jdbc,
                                            Long userId,
                                            String ticketId,
                                            Long groupMessageId,
                                            String business) {
        jdbc.update(
                "INSERT INTO tickets(user_id, ticket_id, group_msg_id) VALUES (?, ?, ?)",
                userId,
                ticketId,
                groupMessageId
        );
        jdbc.update(
                "INSERT INTO messages(id, ticket_id, business) VALUES (?, ?, ?)",
                groupMessageId,
                ticketId,
                business
        );
    }

    private void applyFoundationMigration(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readString(FOUNDATION_MIGRATION).replace("\r\n", "\n");
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }
    }

    private Long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }
}
