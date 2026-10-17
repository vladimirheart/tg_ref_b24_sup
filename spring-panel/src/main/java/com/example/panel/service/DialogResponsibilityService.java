package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogResponsibilityService {

    private static final Logger log = LoggerFactory.getLogger(DialogResponsibilityService.class);
    private static final String AI_AGENT_USERNAME = "ai_agent";

    private static final String EXPECTED_OWNER_SQL = """
        owner.user_id = ? AND owner.ticket_id = ? AND owner.ticket_public_id = ?
        AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
        AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END = ?
        AND owner.is_general = FALSE
        AND 1 = (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = owner.ticket_id)
        AND (owner.business_id IS NOT NULL OR ? = FALSE)
        """;

    // Internal auto-routing grants no operator authority. The proposed BUSINESS assignee
    // must currently have OPERATE, not merely READ or a global page/system role.
    private static final String ASSIGNEE_CAN_OPERATE_SQL = """
        EXISTS (
            SELECT 1 FROM users assignee
            JOIN businesses assignable_business ON assignable_business.id = owner.business_id
             WHERE lower(assignee.username) = lower(assignment.responsible)
               AND assignee.enabled = TRUE AND assignee.is_blocked = FALSE
               AND 1 = (SELECT COUNT(*) FROM users identity_candidate
                         WHERE lower(identity_candidate.username) = lower(assignment.responsible))
               AND assignable_business.status = 'active'
               AND (EXISTS (
                   SELECT 1 FROM business_memberships membership
                    WHERE membership.user_id = assignee.id AND membership.business_id = owner.business_id
                      AND membership.is_active = TRUE
                      AND membership.role_code IN ('OPERATOR', 'MANAGER', 'ADMIN')
               ) OR EXISTS (
                   SELECT 1 FROM business_access_grants access_grant
                    WHERE access_grant.user_id = assignee.id AND access_grant.grant_scope = 'ALL_BUSINESSES'
                      AND access_grant.is_active = TRUE
                      AND access_grant.role_code IN ('OPERATOR', 'MANAGER', 'ADMIN')
               ))
        )
        """;

    private final JdbcTemplate jdbcTemplate;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final MultibusinessFeatureFlags flags;

    public DialogResponsibilityService(JdbcTemplate jdbcTemplate,
                                       LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this(jdbcTemplate, legacyTicketIdJdbcGuard, null);
    }

    @Autowired
    public DialogResponsibilityService(JdbcTemplate jdbcTemplate,
                                       LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                       MultibusinessFeatureFlags flags) {
        this.jdbcTemplate = jdbcTemplate;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.flags = flags;
    }

    /** V61 callbacks must carry their original owner all the way to the assignment statement. */
    public String assignScopedResponsibleIfMissing(UiEventTicketScope scope, String username, String assignedBy) {
        if (scope == null || !scope.isValid() || flags == null) {
            return null;
        }
        String normalizedUsername = trimToNull(username);
        String actor = trimToNull(assignedBy);
        if (normalizedUsername == null || AI_AGENT_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            return loadScopedResponsible(scope);
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by)
                    SELECT assignment.*
                      FROM (VALUES (CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS TEXT)))
                        AS assignment(ticket_id, responsible, assigned_by)
                     WHERE NOT EXISTS (
                         SELECT 1 FROM ticket_responsibles existing WHERE existing.ticket_id = assignment.ticket_id
                     )
                       AND EXISTS (
                           SELECT 1 FROM tickets owner WHERE
                    """ + EXPECTED_OWNER_SQL + """
                           AND (owner.business_id IS NULL OR
                    """ + ASSIGNEE_CAN_OPERATE_SQL + """
                           )
                       )
                    ON CONFLICT (ticket_id) DO NOTHING
                    """,
                    scope.ticketId(), normalizedUsername, actor != null ? actor : normalizedUsername,
                    scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId(), scope.businessId(),
                    scope.scopeKind(), flags.isStrictReadScope()
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to assign scoped responsible for ticket {}: {}", scope.ticketId(),
                    DialogDataAccessSupport.summarizeDataAccessException(ex));
            return null;
        }
        return loadScopedResponsible(scope);
    }

    private String loadScopedResponsible(UiEventTicketScope scope) {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT responsible FROM ticket_responsibles assigned
                     WHERE assigned.ticket_id = ? AND EXISTS (
                         SELECT 1 FROM tickets owner WHERE owner.ticket_id = assigned.ticket_id AND
                    """ + EXPECTED_OWNER_SQL + """
                     ) LIMIT 1
                    """,
                    rs -> rs.next() ? trimToNull(rs.getString("responsible")) : null,
                    scope.ticketId(), scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId(),
                    scope.businessId(), scope.scopeKind(), flags.isStrictReadScope()
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to load scoped responsible for ticket {}: {}", scope.ticketId(),
                    DialogDataAccessSupport.summarizeDataAccessException(ex));
            return null;
        }
    }

    public String assignResponsibleIfMissing(String ticketId, String username) {
        return assignResponsibleIfMissing(ticketId, username, username);
    }

    public String assignResponsibleIfMissing(String ticketId, String username, String assignedBy) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId) || !StringUtils.hasText(username)) {
            return loadResponsible(ticketId);
        }
        String normalizedUsername = trimToNull(username);
        String actor = trimToNull(assignedBy);
        if (normalizedUsername == null || AI_AGENT_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            return loadResponsible(ticketId);
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by)
                    SELECT assignment.*
                      FROM (VALUES (?::text, ?::text, ?::text))
                        AS assignment(ticket_id, responsible, assigned_by)
                     WHERE NOT EXISTS (
                         SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?
                     )
                       AND 1 = (
                           SELECT COUNT(*) FROM tickets unique_ticket
                            WHERE unique_ticket.ticket_id = assignment.ticket_id
                       )
                    """,
                    ticketId, normalizedUsername, actor != null ? actor : normalizedUsername, ticketId
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to assign responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
        return loadResponsible(ticketId);
    }

    public void markDialogAsRead(String ticketId, String operator) {
        if (!legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId) || !StringUtils.hasText(operator)) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE ticket_responsibles "
                            + "SET last_read_at = COALESCE("
                            + "    (SELECT MAX(timestamp) FROM chat_history WHERE ticket_id = ?),"
                            + "    CURRENT_TIMESTAMP"
                            + ") "
                            + "WHERE ticket_id = ? AND responsible = ? "
                            + "AND EXISTS (SELECT 1 FROM tickets panel_ticket "
                            + "WHERE panel_ticket.ticket_id = ticket_responsibles.ticket_id "
                            + "AND panel_ticket.is_general = FALSE) "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket "
                            + "WHERE unique_ticket.ticket_id = ticket_responsibles.ticket_id)",
                    ticketId,
                    ticketId,
                    operator
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId) || !StringUtils.hasText(newResponsible)) {
            return;
        }
        String actor = StringUtils.hasText(assignedBy) ? assignedBy : newResponsible;
        try {
            int inserted = jdbcTemplate.update(
                    """
                    INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by)
                    SELECT assignment.*
                      FROM (VALUES (?::text, ?::text, ?::text))
                        AS assignment(ticket_id, responsible, assigned_by)
                     WHERE NOT EXISTS (
                         SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?
                     )
                       AND 1 = (
                           SELECT COUNT(*) FROM tickets unique_ticket
                            WHERE unique_ticket.ticket_id = assignment.ticket_id
                       )
                    """,
                    ticketId, newResponsible, actor, ticketId
            );
            if (inserted == 0) {
                jdbcTemplate.update(
                        "UPDATE ticket_responsibles SET responsible = ?, assigned_by = ? WHERE ticket_id = ? "
                                + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket "
                                + "WHERE unique_ticket.ticket_id = ticket_responsibles.ticket_id)",
                        newResponsible, actor, ticketId
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to update responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public String loadResponsible(String ticketId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ? "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket "
                            + "WHERE unique_ticket.ticket_id = ticket_responsibles.ticket_id) LIMIT 1",
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
