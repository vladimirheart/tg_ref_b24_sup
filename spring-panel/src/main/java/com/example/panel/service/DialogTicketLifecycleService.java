package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class DialogTicketLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DialogTicketLifecycleService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DialogResponsibilityService dialogResponsibilityService;

    public DialogTicketLifecycleService(JdbcTemplate jdbcTemplate,
                                        DialogResponsibilityService dialogResponsibilityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogResponsibilityService = dialogResponsibilityService;
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        List<String> normalized = normalizeCategories(categories);
        try {
            jdbcTemplate.update("DELETE FROM ticket_categories WHERE ticket_id = ?", ticketId);
            for (String category : normalized) {
                jdbcTemplate.update(
                        "INSERT INTO ticket_categories(ticket_id, category, created_at, updated_at) VALUES(?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        ticketId,
                        category
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to set categories for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
        }
    }

    public DialogService.ResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        if (!StringUtils.hasText(ticketId)) {
            return new DialogService.ResolveResult(false, false, null);
        }
        try {
            List<String> normalizedCategories = normalizeCategories(categories);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Integer.class,
                    ticketId
            );
            if (count == null || count == 0) {
                return new DialogService.ResolveResult(false, false, null);
            }
            if (normalizedCategories.isEmpty()) {
                return new DialogService.ResolveResult(false, true, "Укажите хотя бы одну категорию обращения перед закрытием.");
            }
            String resolvedBy = StringUtils.hasText(operator) ? operator : "Оператор";
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'resolved', resolved_at = CURRENT_TIMESTAMP, "
                            + "resolved_by = ?, closed_count = COALESCE(closed_count, 0) + 1 "
                            + "WHERE ticket_id = ? AND (status IS NULL OR status != 'resolved')",
                    resolvedBy,
                    ticketId
            );
            if (updated > 0) {
                setTicketCategories(ticketId, normalizedCategories);
                ensurePendingFeedbackRequest(ticketId, resolvedBy);
            }
            return new DialogService.ResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
            return new DialogService.ResolveResult(false, false, null);
        }
    }

    public DialogService.ResolveResult reopenTicket(String ticketId, String operator) {
        if (!StringUtils.hasText(ticketId)) {
            return new DialogService.ResolveResult(false, false, null);
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Integer.class,
                    ticketId
            );
            if (count == null || count == 0) {
                return new DialogService.ResolveResult(false, false, null);
            }
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'pending', resolved_at = NULL, resolved_by = NULL, "
                            + "reopen_count = COALESCE(reopen_count, 0) + 1, "
                            + "last_reopen_at = CURRENT_TIMESTAMP "
                            + "WHERE ticket_id = ? AND status = 'resolved'",
                    ticketId
            );
            if (updated > 0 && StringUtils.hasText(operator)) {
                dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
            }
            return new DialogService.ResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to reopen ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
            return new DialogService.ResolveResult(false, false, null);
        }
    }

    private void ensurePendingFeedbackRequest(String ticketId, String resolvedBy) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        String source = isAutoCloseResolvedBy(resolvedBy) ? "auto_close" : "operator_close";
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE pending_feedback_requests "
                            + "SET expires_at = datetime('now', '+1 day'), source = ? "
                            + "WHERE ticket_id = ?",
                    source,
                    ticketId
            );
            if (updated > 0) {
                return;
            }
            TicketOwner owner = jdbcTemplate.query(
                    "SELECT user_id, channel_id FROM tickets WHERE ticket_id = ?",
                    rs -> rs.next()
                            ? new TicketOwner(rs.getLong("user_id"), rs.getLong("channel_id"))
                            : null,
                    ticketId
            );
            if (owner == null) {
                return;
            }
            jdbcTemplate.update(
                    "INSERT INTO pending_feedback_requests "
                            + "(user_id, channel_id, ticket_id, source, created_at, expires_at) "
                            + "VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, datetime('now', '+1 day'))",
                    owner.userId,
                    owner.channelId,
                    ticketId,
                    source
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to ensure pending feedback request for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
        }
    }

    private boolean isAutoCloseResolvedBy(String resolvedBy) {
        if (!StringUtils.hasText(resolvedBy)) {
            return false;
        }
        String normalized = resolvedBy.trim().toLowerCase(Locale.ROOT);
        return "авто-система".equals(normalized) || "auto_close".equals(normalized);
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

    private record TicketOwner(long userId, long channelId) {
    }
}
