package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
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
    static final String ASSIGNEE_CAN_OPERATE_SQL = """
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
    private final LegacyDialogAssignmentWriter legacyWriter;
    private final PlatformTransactionManager transactions;

    public DialogResponsibilityService(JdbcTemplate jdbcTemplate,
                                       LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this(jdbcTemplate, legacyTicketIdJdbcGuard, null);
    }

    public DialogResponsibilityService(JdbcTemplate jdbcTemplate,
                                       LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                       MultibusinessFeatureFlags flags) {
        this(jdbcTemplate, legacyTicketIdJdbcGuard, flags, null);
    }

    @Autowired
    public DialogResponsibilityService(JdbcTemplate jdbcTemplate,
                                       LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                       MultibusinessFeatureFlags flags,
                                       @Qualifier("transactionManager") PlatformTransactionManager transactions) {
        this.jdbcTemplate = jdbcTemplate;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.flags = flags;
        this.transactions = transactions;
        this.legacyWriter = new LegacyDialogAssignmentWriter(jdbcTemplate,flags,transactions);
    }

    /** V61 callbacks must carry their original owner all the way to the assignment statement. */
    public String assignScopedResponsibleIfMissing(UiEventTicketScope scope, String username, String assignedBy) {
        if (scope == null || !scope.isValid() || flags == null) {
            return null;
        }
        String normalizedUsername = exactIdentityToNull(username);
        String actor = exactIdentityToNull(assignedBy);
        if (normalizedUsername == null || AI_AGENT_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            return loadScopedResponsible(scope);
        }
        PlatformTransactionManager manager = transactions;
        if (manager == null && jdbcTemplate.getDataSource() != null)
            manager = new DataSourceTransactionManager(jdbcTemplate.getDataSource());
        if (manager == null) return null;
        try {
            return new TransactionTemplate(manager).execute(status -> {
                // Match manual/SLA/legacy writers: advisory raw-ID lock, root, then responsibility.
                new LegacyTicketIdConcurrencyGuard(jdbcTemplate).acquire(scope.ticketId());
                jdbcTemplate.query("SELECT user_id FROM tickets WHERE ticket_id = ? FOR UPDATE",
                        (rs, row) -> rs.getLong("user_id"), scope.ticketId());
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
                return loadScopedResponsible(scope);
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to assign scoped responsible for ticket {}: {}", scope.ticketId(),
                    DialogDataAccessSupport.summarizeDataAccessException(ex));
            return null;
        }
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
                    rs -> rs.next() ? exactIdentityToNull(rs.getString("responsible")) : null,
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

    /** Only a current unambiguous UNRESOLVED owner may use bare-ID assignment before strict scope. */
    public String assignResponsibleIfMissing(String ticketId,String username,String assignedBy) {
        if(!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId))return null;
        String target=trimToNull(username),actor=trimToNull(assignedBy);
        return legacyWriter.execute(ticketId,target,actor!=null?actor:target,false,LegacyAssignment::responsible);
    }

    public boolean canAssignLegacyResponsible(String ticketId) {return legacyWriter.allowed(ticketId);}

    public record LegacyAssignment(boolean allowed,boolean changed,String responsible) { }

    /** The accepted callback runs while the canonical root lock is held. A denied outcome must have no side effects. */
    public <T> T withLegacyRedirect(String ticketId,String responsible,String assignedBy,java.util.function.Function<LegacyAssignment,T> after) {
        if(!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)||!StringUtils.hasText(responsible))
            return after.apply(new LegacyAssignment(false,false,null));
        String actor=StringUtils.hasText(assignedBy)?assignedBy:responsible;
        return legacyWriter.execute(ticketId,responsible,actor,true,after);
    }

    /** Bare-ID read marks are compatibility writes for UNRESOLVED before strict scope. */
    public void markDialogAsRead(String ticketId, String operator) {
        if ((flags != null && flags.isStrictReadScope())
                || !legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId) || !StringUtils.hasText(operator)) {
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
                            + "AND panel_ticket.is_general = FALSE AND panel_ticket.business_id IS NULL) "
                            + "AND 1 = (SELECT COUNT(*) FROM tickets unique_ticket "
                            + "WHERE unique_ticket.ticket_id = ticket_responsibles.ticket_id) "
                            + "AND ? = FALSE",
                    ticketId,
                    ticketId,
                    operator,
                    flags != null && flags.isStrictReadScope()
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public boolean assignResponsibleIfMissingOrRedirected(String ticketId,String responsible,String assignedBy) {
        return withLegacyRedirect(ticketId,responsible,assignedBy,result->result.allowed()&&result.changed());
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

    // Scoped identities are account keys; only the legacy paths trim freeform values.
    private String exactIdentityToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
