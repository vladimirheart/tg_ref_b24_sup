package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogResponsibilityService {

    private static final Logger log = LoggerFactory.getLogger(DialogResponsibilityService.class);
    private static final String AI_AGENT_USERNAME = "ai_agent";

    private final JdbcTemplate jdbcTemplate;

    public DialogResponsibilityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String assignResponsibleIfMissing(String ticketId, String username) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(username)) {
            return loadResponsible(ticketId);
        }
        String normalizedUsername = trimToNull(username);
        if (normalizedUsername == null || AI_AGENT_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            return loadResponsible(ticketId);
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) "
                            + "SELECT ?, ?, ? WHERE NOT EXISTS ("
                            + "SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?)",
                    ticketId, normalizedUsername, normalizedUsername, ticketId
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to assign responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
        return loadResponsible(ticketId);
    }

    public void markDialogAsRead(String ticketId, String operator) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(operator)) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE ticket_responsibles "
                            + "SET last_read_at = COALESCE("
                            + "    (SELECT MAX(timestamp) FROM chat_history WHERE ticket_id = ?),"
                            + "    CURRENT_TIMESTAMP"
                            + ") "
                            + "WHERE ticket_id = ? AND responsible = ?",
                    ticketId,
                    ticketId,
                    operator
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(newResponsible)) {
            return;
        }
        String actor = StringUtils.hasText(assignedBy) ? assignedBy : newResponsible;
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) "
                            + "SELECT ?, ?, ? WHERE NOT EXISTS ("
                            + "SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?)",
                    ticketId, newResponsible, actor, ticketId
            );
            if (inserted == 0) {
                jdbcTemplate.update(
                        "UPDATE ticket_responsibles SET responsible = ?, assigned_by = ? WHERE ticket_id = ?",
                        newResponsible, actor, ticketId
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to update responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public String loadResponsible(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ? LIMIT 1",
                    rs -> rs.next() ? trimToNull(rs.getString("responsible")) : null,
                    ticketId
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to load responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return null;
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
