package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyTicketIdJdbcGuardTest {

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
