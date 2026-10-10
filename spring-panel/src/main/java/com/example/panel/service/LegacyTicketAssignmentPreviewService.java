package com.example.panel.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Produces a fail-closed preview for a separately approved legacy-ticket
 * assignment. This service has no write operation and is not exposed by an
 * endpoint: applying a preview remains an explicit migration/backfill step.
 */
@Service
public class LegacyTicketAssignmentPreviewService {

    private final JdbcTemplate jdbcTemplate;

    public LegacyTicketAssignmentPreviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolves only byte-for-byte approved aliases. In particular, this method
     * does not trim, lowercase, or otherwise infer an alias from legacy data.
     */
    public List<AssignmentPreview> preview() {
        return jdbcTemplate.query(
                """
                SELECT ticket.user_id,
                       ticket.ticket_id,
                       ticket.business_id AS current_business_id,
                       message.business AS legacy_alias,
                       COUNT(DISTINCT alias.business_id) AS matched_business_count,
                       MIN(alias.business_id) AS matched_business_id
                  FROM tickets ticket
             LEFT JOIN messages message ON message.id = ticket.group_msg_id
             LEFT JOIN business_legacy_aliases alias
                    ON convert_to(alias.alias, 'UTF8') = convert_to(message.business, 'UTF8')
                   AND alias.is_active = TRUE
              GROUP BY ticket.user_id, ticket.ticket_id, ticket.business_id, message.business
              ORDER BY ticket.user_id, ticket.ticket_id
                """,
                (resultSet, rowNum) -> toPreview(
                        resultSet.getLong("user_id"),
                        resultSet.getString("ticket_id"),
                        resultSet.getObject("current_business_id", Long.class),
                        resultSet.getString("legacy_alias"),
                        resultSet.getLong("matched_business_count"),
                        resultSet.getObject("matched_business_id", Long.class)
                )
        );
    }

    private AssignmentPreview toPreview(Long userId,
                                        String ticketId,
                                        Long currentBusinessId,
                                        String legacyAlias,
                                        long matchedBusinessCount,
                                        Long matchedBusinessId) {
        if (matchedBusinessCount > 1L) {
            return new AssignmentPreview(
                    userId,
                    ticketId,
                    currentBusinessId,
                    legacyAlias,
                    AssignmentState.AMBIGUOUS_MAPPING,
                    null
            );
        }
        if (matchedBusinessCount == 1L) {
            if (currentBusinessId == null) {
                return new AssignmentPreview(
                        userId,
                        ticketId,
                        null,
                        legacyAlias,
                        AssignmentState.READY_TO_ASSIGN,
                        matchedBusinessId
                );
            }
            return new AssignmentPreview(
                    userId,
                    ticketId,
                    currentBusinessId,
                    legacyAlias,
                    currentBusinessId.equals(matchedBusinessId)
                            ? AssignmentState.ALREADY_ASSIGNED
                            : AssignmentState.CONFLICT,
                    matchedBusinessId
            );
        }
        return new AssignmentPreview(
                userId,
                ticketId,
                currentBusinessId,
                legacyAlias,
                AssignmentState.UNRESOLVED,
                null
        );
    }

    public enum AssignmentState {
        READY_TO_ASSIGN,
        ALREADY_ASSIGNED,
        UNRESOLVED,
        AMBIGUOUS_MAPPING,
        CONFLICT
    }

    public record AssignmentPreview(Long userId,
                                    String ticketId,
                                    Long currentBusinessId,
                                    String legacyAlias,
                                    AssignmentState state,
                                    Long proposedBusinessId) {
    }
}
