package com.example.panel.service.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LegacyTicketIdConcurrencyGuard {

    private static final String LOCK_SQL =
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";

    private final JdbcTemplate jdbcTemplate;

    public LegacyTicketIdConcurrencyGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            throw new IllegalArgumentException("Ingress requires a legacy ticket ID");
        }
        // PostgreSQL releases the xact lock together with the surrounding transaction.
        jdbcTemplate.query(
            LOCK_SQL,
            statement -> statement.setString(1, ticketId),
            resultSet -> null
        );
    }
}
