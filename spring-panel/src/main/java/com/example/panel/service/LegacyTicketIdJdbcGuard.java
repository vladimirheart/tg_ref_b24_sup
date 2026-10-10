package com.example.panel.service;

import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fail-closed compatibility guard for legacy call sites that cannot provide a
 * canonical ticket tuple. A bare ticket ID is usable only when it resolves to
 * exactly one ticket.
 */
@Component
public class LegacyTicketIdJdbcGuard {

    private final JdbcTemplate jdbcTemplate;

    public LegacyTicketIdJdbcGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasUniqueTicket(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return false;
        }
        try {
            List<Integer> candidates = jdbcTemplate.query(
                "SELECT 1 FROM tickets WHERE ticket_id = ? LIMIT 2",
                (rs, rowNum) -> 1,
                ticketId.trim()
            );
            return candidates.size() == 1;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
