package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class DialogTicketLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DialogTicketLifecycleService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final MultibusinessFeatureFlags flags;

    public DialogTicketLifecycleService(JdbcTemplate jdbcTemplate,
                                        DialogResponsibilityService dialogResponsibilityService,
                                        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this(jdbcTemplate, dialogResponsibilityService, legacyTicketIdJdbcGuard, null);
    }

    @Autowired
    public DialogTicketLifecycleService(JdbcTemplate jdbcTemplate,
                                        DialogResponsibilityService dialogResponsibilityService,
                                        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                        MultibusinessFeatureFlags flags) {
        this.flags = flags;
        this.jdbcTemplate = jdbcTemplate;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        if (!canMutateLegacyTicket(ticketId)) {
            return;
        }
        List<String> normalized = normalizeCategories(categories);
        try {
            jdbcTemplate.update("""
                    DELETE FROM ticket_categories
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = ticket_categories.ticket_id)
                    %s
                    """.formatted(legacyClause("ticket_categories.ticket_id")), ticketId, strictLegacy());
            for (String category : normalized) {
                jdbcTemplate.update(
                        """
                        INSERT INTO ticket_categories(ticket_id, category, created_at, updated_at)
                        SELECT category_row.ticket_id, category_row.category, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                          FROM (VALUES (?::text, ?::text)) AS category_row(ticket_id, category)
                         WHERE 1 = (SELECT COUNT(*)
                                      FROM tickets unique_ticket
                                     WHERE unique_ticket.ticket_id = category_row.ticket_id)
                        %s
                        """.formatted(legacyClause("category_row.ticket_id")),
                        ticketId,
                        category,
                        strictLegacy()
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to set categories for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public DialogResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        if (!canMutateLegacyTicket(ticketId)) {
            return new DialogResolveResult(false, false, null);
        }
        try {
            List<String> normalizedCategories = normalizeCategories(categories);
            String currentStatus = jdbcTemplate.query(
                    """
                    SELECT status FROM tickets
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = tickets.ticket_id)
                    %s
                    """.formatted(legacyClause("tickets.ticket_id")),
                    rs -> rs.next() ? trimToNull(rs.getString("status")) : null,
                    ticketId,
                    strictLegacy()
            );
            if (isClosedStatus(currentStatus)) {
                return new DialogResolveResult(false, true, "already_closed");
            }
            if (normalizedCategories.isEmpty()) {
                return new DialogResolveResult(false, true, "Укажите хотя бы одну категорию обращения перед закрытием.");
            }
            String resolvedBy = StringUtils.hasText(operator) ? operator : "Оператор";
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'resolved', resolved_at = CURRENT_TIMESTAMP, "
                            + "resolved_by = ?, closed_count = COALESCE(closed_count, 0) + 1 "
                            + "WHERE ticket_id = ? AND (status IS NULL OR lower(status) NOT IN ('resolved', 'closed')) "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket WHERE unique_ticket.ticket_id = tickets.ticket_id)"
                            + legacyClause("tickets.ticket_id"),
                    resolvedBy,
                    ticketId,
                    strictLegacy()
            );
            if (updated > 0) {
                clearTicketActivity(ticketId);
                setTicketCategories(ticketId, normalizedCategories);
                ensurePendingFeedbackRequest(ticketId, resolvedBy);
            }
            return new DialogResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return new DialogResolveResult(false, false, null);
        }
    }

    public DialogResolveResult reopenTicket(String ticketId, String operator) {
        if (!canMutateLegacyTicket(ticketId)) {
            return new DialogResolveResult(false, false, null);
        }
        try {
            String currentStatus = jdbcTemplate.query(
                    """
                    SELECT status FROM tickets
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = tickets.ticket_id)
                    %s
                    """.formatted(legacyClause("tickets.ticket_id")),
                    rs -> rs.next() ? trimToNull(rs.getString("status")) : null,
                    ticketId,
                    strictLegacy()
            );
            if (!isClosedStatus(currentStatus)) {
                return new DialogResolveResult(false, true, "not_closed");
            }
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'pending', resolved_at = NULL, resolved_by = NULL, "
                            + "reopen_count = COALESCE(reopen_count, 0) + 1, "
                            + "last_reopen_at = CURRENT_TIMESTAMP "
                            + "WHERE ticket_id = ? AND lower(COALESCE(status, '')) IN ('resolved', 'closed') "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket WHERE unique_ticket.ticket_id = tickets.ticket_id)"
                            + legacyClause("tickets.ticket_id"),
                    ticketId,
                    strictLegacy()
            );
            if (updated > 0) {
                restoreTicketActivity(ticketId);
                if (StringUtils.hasText(operator) && legacyOwnerCurrent(ticketId)) {
                    dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
                }
            }
            return new DialogResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to reopen ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return new DialogResolveResult(false, false, null);
        }
    }

    private void ensurePendingFeedbackRequest(String ticketId, String resolvedBy) {
        if (!canMutateLegacyTicket(ticketId)) {
            return;
        }
        String source = isAutoCloseResolvedBy(resolvedBy) ? "auto_close" : "operator_close";
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusDays(1);
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE pending_feedback_requests "
                            + "SET expires_at = ?, source = ? "
                            + "WHERE ticket_id = ? "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket "
                            + "WHERE unique_ticket.ticket_id = pending_feedback_requests.ticket_id)"
                            + legacyClause("pending_feedback_requests.ticket_id"),
                    expiresAt,
                    source,
                    ticketId,
                    strictLegacy()
            );
            if (updated > 0) {
                return;
            }
            TicketOwner owner = jdbcTemplate.query(
                    """
                    SELECT user_id, channel_id FROM tickets
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = tickets.ticket_id)
                    %s
                    """.formatted(legacyClause("tickets.ticket_id")),
                    rs -> rs.next()
                            ? new TicketOwner(rs.getLong("user_id"), rs.getLong("channel_id"))
                            : null,
                    ticketId,
                    strictLegacy()
            );
            if (owner == null) {
                return;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO pending_feedback_requests
                        (user_id, channel_id, ticket_id, source, created_at, expires_at)
                    SELECT feedback_row.user_id, feedback_row.channel_id, feedback_row.ticket_id,
                           feedback_row.source, feedback_row.created_at, feedback_row.expires_at
                      FROM (VALUES (?::bigint, ?::bigint, ?::text, ?::text, ?::timestamptz, ?::timestamptz))
                           AS feedback_row(user_id, channel_id, ticket_id, source, created_at, expires_at)
                     WHERE 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = feedback_row.ticket_id)
                    %s
                    """.formatted(legacyClause("feedback_row.ticket_id")),
                    owner.userId,
                    owner.channelId,
                    ticketId,
                    source,
                    createdAt,
                    expiresAt,
                    strictLegacy()
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to ensure pending feedback request for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    private void clearTicketActivity(String ticketId) {
        if (!legacyOwnerCurrent(ticketId)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    DELETE FROM ticket_active
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = ticket_active.ticket_id)
                    %s
                    """.formatted(legacyClause("ticket_active.ticket_id")), ticketId, strictLegacy());
        } catch (DataAccessException ex) {
            log.warn("Unable to clear ticket_active for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    private void restoreTicketActivity(String ticketId) {
        if (!legacyOwnerCurrent(ticketId)) {
            return;
        }
        try {
            String identity = jdbcTemplate.query(
                    """
                    SELECT user_id FROM tickets
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = tickets.ticket_id)
                    %s
                    """.formatted(legacyClause("tickets.ticket_id")),
                    rs -> rs.next() ? trimToNull(rs.getString("user_id")) : null,
                    ticketId,
                    strictLegacy()
            );
            if (!StringUtils.hasText(identity)) {
                return;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE ticket_active
                       SET user_identity = CASE
                               WHEN user_identity IS NULL OR trim(user_identity) = '' THEN ?
                               ELSE user_identity
                           END,
                           last_seen = CURRENT_TIMESTAMP
                     WHERE ticket_id = ?
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = ticket_active.ticket_id)
                    %s
                    """.formatted(legacyClause("ticket_active.ticket_id")),
                    identity,
                    ticketId,
                    strictLegacy()
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                        INSERT INTO ticket_active(ticket_id, user_identity, last_seen)
                        SELECT active_row.ticket_id, active_row.user_identity, CURRENT_TIMESTAMP
                          FROM (VALUES (?::text, ?::text)) AS active_row(ticket_id, user_identity)
                         WHERE 1 = (SELECT COUNT(*)
                                      FROM tickets unique_ticket
                                     WHERE unique_ticket.ticket_id = active_row.ticket_id)
                        %s
                        """.formatted(legacyClause("active_row.ticket_id")),
                        ticketId,
                        identity,
                        strictLegacy()
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to restore ticket_active for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    /** Bare-ID mutations have authority only in the legacy UNRESOLVED namespace before strict. */
    public boolean canMutateLegacyTicket(String ticketId) {
        return !strictLegacy() && StringUtils.hasText(ticketId)
            && legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId) && legacyOwnerCurrent(ticketId);
    }

    private boolean legacyOwnerCurrent(String ticketId) {
        if (strictLegacy() || !StringUtils.hasText(ticketId)) {
            return false;
        }
        try {
            boolean current = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM tickets owner WHERE owner.ticket_id = ?"
                            + " AND owner.business_id IS NULL AND owner.is_general = FALSE"
                            + " AND 1 = (SELECT count(*) FROM tickets collision WHERE collision.ticket_id = owner.ticket_id)"
                            + " AND ? = FALSE)",
                    Boolean.class, ticketId, strictLegacy()));
            return current && !strictLegacy();
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private String legacyClause(String reference) {
        if ("tickets.ticket_id".equals(reference)) {
            // Root UPDATE must recheck the current row after waiting on a concurrent owner change,
            // rather than authorizing it through an older subquery snapshot.
            return " AND tickets.business_id IS NULL AND tickets.is_general = FALSE AND ? = FALSE ";
        }
        return " AND EXISTS(SELECT 1 FROM tickets legacy_owner WHERE legacy_owner.ticket_id = " + reference
                + " AND legacy_owner.business_id IS NULL AND legacy_owner.is_general = FALSE) AND ? = FALSE ";
    }

    private boolean strictLegacy() {
        return flags != null && flags.isStrictReadScope();
    }

    private boolean isAutoCloseResolvedBy(String resolvedBy) {
        if (!StringUtils.hasText(resolvedBy)) {
            return false;
        }
        String normalized = resolvedBy.trim().toLowerCase(Locale.ROOT);
        return "авто-система".equals(normalized) || "auto_close".equals(normalized);
    }

    private boolean isClosedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "resolved".equals(normalized) || "closed".equals(normalized);
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record TicketOwner(long userId, long channelId) {
    }
}
