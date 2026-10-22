package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DialogAuditService {

    private static final Logger log = LoggerFactory.getLogger(DialogAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final com.example.panel.config.MultibusinessFeatureFlags flags;

    public DialogAuditService(JdbcTemplate jdbcTemplate, LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this(jdbcTemplate,legacyTicketIdJdbcGuard,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public DialogAuditService(JdbcTemplate jdbcTemplate,LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
            com.example.panel.config.MultibusinessFeatureFlags flags) {
        this.flags=flags;
        this.jdbcTemplate = jdbcTemplate;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(action)) {
            return;
        }
        boolean assignment=java.util.Set.of("take","reassign").contains(action.trim());
        if(assignment&&flags!=null&&flags.isStrictReadScope())return;
        try {
            if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO dialog_action_audit (ticket_id, actor, action, result, detail, created_at)
                    SELECT audit_row.ticket_id, audit_row.actor, audit_row.action, audit_row.result,
                           audit_row.detail, CURRENT_TIMESTAMP
                      FROM (VALUES (?::text, ?::text, ?::text, ?::text, ?::text))
                           AS audit_row(ticket_id, actor, action, result, detail)
                     WHERE 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = audit_row.ticket_id)
                    %s
                    """.formatted(assignment?"AND EXISTS(SELECT 1 FROM tickets legacy_owner WHERE legacy_owner.ticket_id=audit_row.ticket_id AND legacy_owner.is_general=FALSE AND legacy_owner.business_id IS NULL)":""),
                    ticketId,
                    StringUtils.hasText(actor) ? actor.trim() : "anonymous",
                    action.trim(),
                    StringUtils.hasText(result) ? result.trim() : "unknown",
                    StringUtils.hasText(detail) ? detail.trim() : null);
        } catch (DataAccessException ex) {
            log.warn("Unable to persist dialog action audit for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public boolean hasSuccessfulDialogAction(String ticketId, String action) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(action)) {
            return false;
        }
        try {
            if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
                return false;
            }
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM dialog_action_audit
                     WHERE ticket_id = ?
                       AND action = ?
                       AND result = 'success'
                       AND 1 = (SELECT COUNT(*)
                                  FROM tickets unique_ticket
                                 WHERE unique_ticket.ticket_id = dialog_action_audit.ticket_id)
                    """,
                    Long.class,
                    ticketId,
                    action.trim());
            return count != null && count > 0L;
        } catch (DataAccessException ex) {
            log.warn("Unable to read dialog action audit for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return false;
        }
    }

    public void logWorkspaceTelemetry(String actor,
                                      String eventType,
                                      String eventGroup,
                                      String ticketId,
                                      String reason,
                                      String errorCode,
                                      String contractVersion,
                                      Long durationMs,
                                      String experimentName,
                                      String experimentCohort,
                                      String operatorSegment,
                                      List<String> primaryKpis,
                                      List<String> secondaryKpis,
                                      String templateId,
                                      String templateName) {
        if (!StringUtils.hasText(eventType)) {
            return;
        }
        try {
            if (StringUtils.hasText(ticketId) && !legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    )
                    SELECT telemetry.actor, telemetry.event_type, telemetry.event_group, telemetry.ticket_id,
                           telemetry.reason, telemetry.error_code, telemetry.contract_version, telemetry.duration_ms,
                           telemetry.experiment_name, telemetry.experiment_cohort, telemetry.operator_segment,
                           telemetry.primary_kpis, telemetry.secondary_kpis, telemetry.template_id,
                           telemetry.template_name, CURRENT_TIMESTAMP
                      FROM (VALUES (?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text,
                                    ?::bigint, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text))
                           AS telemetry(actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                                        primary_kpis, secondary_kpis, template_id, template_name)
                    %s
                    """.formatted(StringUtils.hasText(ticketId)
                            ? "WHERE 1 = (SELECT COUNT(*) FROM tickets unique_ticket WHERE unique_ticket.ticket_id = telemetry.ticket_id)"
                            : ""),
                    trimOrNull(actor),
                    trimOrNull(eventType),
                    trimOrNull(eventGroup),
                    StringUtils.hasText(ticketId) ? ticketId : null,
                    trimOrNull(reason),
                    trimOrNull(errorCode),
                    trimOrNull(contractVersion),
                    durationMs,
                    trimOrNull(experimentName),
                    trimOrNull(experimentCohort),
                    trimOrNull(operatorSegment),
                    joinCsv(primaryKpis),
                    joinCsv(secondaryKpis),
                    trimOrNull(templateId),
                    trimOrNull(templateName));
        } catch (DataAccessException ex) {
            log.warn("Unable to persist workspace telemetry event '{}': {}", eventType, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
