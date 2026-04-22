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

    public DialogAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(action)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO dialog_action_audit (ticket_id, actor, action, result, detail, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
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
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM dialog_action_audit
                     WHERE ticket_id = ?
                       AND action = ?
                       AND result = 'success'
                    """,
                    Long.class,
                    ticketId.trim(),
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
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trimOrNull(actor),
                    trimOrNull(eventType),
                    trimOrNull(eventGroup),
                    trimOrNull(ticketId),
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
