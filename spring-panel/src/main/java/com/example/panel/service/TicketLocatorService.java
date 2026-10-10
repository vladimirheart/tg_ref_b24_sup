package com.example.panel.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves a canonical ticket identity without treating a bare legacy ticket ID
 * as globally unique.
 */
@Service
public class TicketLocatorService {

    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

    private final JdbcTemplate jdbcTemplate;

    public TicketLocatorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TicketReference> findByPublicId(String ticketPublicId) {
        if (!isPublicId(ticketPublicId)) {
            return Optional.empty();
        }
        return findSingle("""
                SELECT user_id, ticket_id, ticket_public_id, business_id
                  FROM tickets
                 WHERE ticket_public_id = ?
                """, ticketPublicId);
    }

    public Optional<TicketReference> findByCompositeKey(Long userId, String ticketId) {
        if (userId == null || userId <= 0L || !StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        return findSingle("""
                SELECT user_id, ticket_id, ticket_public_id, business_id
                  FROM tickets
                 WHERE user_id = ?
                   AND ticket_id = ?
                """, userId, ticketId.trim());
    }

    public boolean isPublicId(String value) {
        return value != null && PUBLIC_ID_PATTERN.matcher(value).matches();
    }

    private Optional<TicketReference> findSingle(String sql, Object... arguments) {
        List<TicketReference> matches = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new TicketReference(
                        rs.getLong("user_id"),
                        rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"),
                        rs.getObject("business_id", Long.class)
                ),
                arguments
        );
        return matches.stream().findFirst();
    }

    public record TicketReference(
            long userId,
            String ticketId,
            String ticketPublicId,
            Long businessId
    ) {
    }
}
