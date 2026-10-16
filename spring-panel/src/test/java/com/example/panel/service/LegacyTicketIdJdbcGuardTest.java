package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyTicketIdJdbcGuardTest {

    @Test
    void legacyPanelExcludesGeneralWithoutReclassifyingUnresolvedOrHidingCollisions() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("legacy_panel_scope_r118");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, business_id BIGINT,
                    is_general BOOLEAN, PRIMARY KEY(user_id,ticket_id))
                """);
        jdbc.update("""
                INSERT INTO tickets VALUES
                  (1,'GENERAL',NULL,TRUE),(2,'BUSINESS',7,FALSE),(3,'UNRESOLVED',NULL,FALSE),
                  (4,'COLLISION',7,FALSE),(5,'COLLISION',NULL,TRUE),
                  (6,' T/Ю?&%+ ',NULL,FALSE),(7,'UNKNOWN',NULL,NULL)
                """);
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        assertThat(guard.hasUniqueTicket("GENERAL")).isTrue();
        assertThat(guard.hasUniqueNonGeneralTicket("GENERAL")).isFalse();
        assertThat(guard.hasUniqueNonGeneralTicket("BUSINESS")).isTrue();
        assertThat(guard.hasUniqueNonGeneralTicket("UNRESOLVED")).isTrue();
        assertThat(guard.hasUniqueNonGeneralTicket("COLLISION")).isFalse();
        assertThat(guard.hasUniqueNonGeneralTicket(" T/Ю?&%+ ")).isTrue();
        for (String id : new String[]{"T/Ю?&%+", "UNKNOWN", "ABSENT", " ", null}) {
            assertThat(guard.hasUniqueNonGeneralTicket(id)).as("id=%s", id).isFalse();
        }
    }

    @Test
    void missingMarkerOrUnavailableDatabaseCannotAuthorizeLegacyPanel() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("legacy_panel_missing_marker_r118");
        jdbc.execute("CREATE TABLE tickets(ticket_id TEXT)");
        jdbc.update("INSERT INTO tickets VALUES ('T')");
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        assertThat(guard.hasUniqueNonGeneralTicket("T")).isFalse();
        assertThat(guard.hasUniqueTicket("T")).isTrue();
        jdbc.execute("DROP TABLE tickets");
        assertThat(guard.hasUniqueNonGeneralTicket("T")).isFalse();
    }

    @Test
    void acceptsOnlyExactlyOneCandidate() {
        JdbcTemplate jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("legacy_ticket_id_jdbc_guard");
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT,
                    ticket_id TEXT,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbcTemplate);

        assertThat(guard.hasUniqueTicket("T-ABSENT")).isFalse();
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?)", 1L, "T-ONE");
        assertThat(guard.hasUniqueTicket("T-ONE")).isTrue();
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?), (?, ?)",
                2L, "T-AMB", 3L, "T-AMB");
        assertThat(guard.hasUniqueTicket("T-AMB")).isFalse();
    }

    @Test
    void preservesExactLegacyTicketIdIncludingEdgeWhitespace() {
        JdbcTemplate jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("legacy_ticket_id_jdbc_exact_r10");
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT,
                    ticket_id TEXT,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?), (?, ?)",
                1L, "T-PLAIN", 2L, "  T-LEADING");
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbcTemplate);

        assertThat(guard.hasUniqueTicket("T-PLAIN")).isTrue();
        assertThat(guard.hasUniqueTicket(" T-PLAIN")).isFalse();
        assertThat(guard.hasUniqueTicket("T-PLAIN ")).isFalse();
        assertThat(guard.hasUniqueTicket("  T-LEADING")).isTrue();
        assertThat(guard.hasUniqueTicket("T-LEADING")).isFalse();
        assertThat(guard.hasUniqueTicket("  T-LEADING ")).isFalse();
        assertThat(guard.hasUniqueTicket(" ")).isFalse();
    }
}
