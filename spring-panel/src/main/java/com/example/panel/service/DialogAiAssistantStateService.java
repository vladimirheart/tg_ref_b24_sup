package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogAiAssistantStateService {

    private final JdbcTemplate jdbcTemplate;
    private final DialogAiAssistantPersistenceService persistenceService;

    public DialogAiAssistantStateService(JdbcTemplate jdbcTemplate,
                                         DialogAiAssistantPersistenceService persistenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
    }

    public Map<String, Object> loadDialogControlState(String ticketId) {
        DialogAiControl control = loadDialogControl(ticketId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", persistenceService.trim(ticketId));
        payload.put("ai_disabled", control.aiDisabled());
        payload.put("auto_reply_blocked", control.autoReplyBlocked());
        payload.put("reason", control.reason());
        payload.put("updated_by", control.updatedBy());
        payload.put("updated_at", control.updatedAt());
        return payload;
    }

    public boolean updateDialogControlState(String ticketId,
                                            Boolean aiDisabled,
                                            Boolean autoReplyBlocked,
                                            String reason,
                                            String actor,
                                            String mode) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return false;
        }
        DialogAiControl current = loadDialogControl(ticket);
        boolean nextAiDisabled = aiDisabled != null ? aiDisabled : current.aiDisabled();
        boolean nextAutoReplyBlocked = autoReplyBlocked != null ? autoReplyBlocked : current.autoReplyBlocked();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked, reason, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        ai_disabled = excluded.ai_disabled,
                        auto_reply_blocked = excluded.auto_reply_blocked,
                        reason = excluded.reason,
                        updated_by = excluded.updated_by,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    ticket,
                    nextAiDisabled ? 1 : 0,
                    nextAutoReplyBlocked ? 1 : 0,
                    persistenceService.cut(reason, 500),
                    persistenceService.trim(actor)
            );
            persistenceService.recordAiEvent(
                    ticket,
                    "ai_agent_control_changed",
                    persistenceService.trim(actor),
                    "control_update",
                    "dialog_control_updated",
                    null,
                    null,
                    persistenceService.trim(reason),
                    Map.of(
                            "ai_disabled", nextAiDisabled,
                            "auto_reply_blocked", nextAutoReplyBlocked,
                            "policy_stage", "dialog_control",
                            "policy_outcome", "updated",
                            "mode", persistenceService.trim(mode)
                    )
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isProcessing(String ticketId) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return false;
        }
        try {
            Integer value = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(is_processing, 0) FROM ticket_ai_agent_state WHERE ticket_id = ?",
                    Integer.class,
                    ticket
            );
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public void clearProcessing(String ticketId,
                                String action,
                                String error,
                                String decisionType,
                                String decisionReason,
                                String sourceHits,
                                String mode) {
        upsertState(
                ticketId,
                false,
                mode,
                action,
                error,
                null,
                null,
                null,
                decisionType,
                decisionReason,
                sourceHits
        );
    }

    public void clearProcessing(String ticketId, String action, String error) {
        persistenceService.clearProcessing(ticketId, action, error);
    }

    public void markProcessing(String ticketId,
                               String action,
                               String error,
                               String source,
                               Double score,
                               String suggestedReply,
                               String decisionType,
                               String decisionReason,
                               String sourceHits,
                               String mode) {
        upsertState(
                ticketId,
                true,
                mode,
                action,
                error,
                source,
                score,
                suggestedReply,
                decisionType,
                decisionReason,
                sourceHits
        );
    }

    public boolean hasOpenCorrectionRequest(String ticketId) {
        String lastAction = jdbcTemplate.query(
                "SELECT last_action FROM ticket_ai_agent_state WHERE ticket_id=? LIMIT 1",
                rs -> rs.next() ? persistenceService.trim(rs.getString("last_action")) : null,
                ticketId
        );
        return "operator_correction_requested".equalsIgnoreCase(String.valueOf(lastAction));
    }

    public String loadLastSuggestedReply(String ticketId) {
        return jdbcTemplate.query(
                "SELECT last_suggested_reply FROM ticket_ai_agent_state WHERE ticket_id=? LIMIT 1",
                rs -> rs.next() ? persistenceService.trim(rs.getString("last_suggested_reply")) : null,
                ticketId
        );
    }

    public DialogAiControl loadDialogControl(String ticketId) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return DialogAiControl.DEFAULT;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ai_disabled, auto_reply_blocked, reason, updated_by, updated_at FROM ticket_ai_agent_dialog_control WHERE ticket_id = ? LIMIT 1",
                    ticket
            );
            if (rows.isEmpty()) {
                return DialogAiControl.DEFAULT;
            }
            Map<String, Object> row = rows.get(0);
            return new DialogAiControl(
                    persistenceService.isTrue(row.get("ai_disabled")),
                    persistenceService.isTrue(row.get("auto_reply_blocked")),
                    persistenceService.trim(persistenceService.safe(row.get("reason"))),
                    persistenceService.trim(persistenceService.safe(row.get("updated_by"))),
                    persistenceService.trim(persistenceService.safe(row.get("updated_at")))
            );
        } catch (Exception ex) {
            return DialogAiControl.DEFAULT;
        }
    }

    private void upsertState(String ticketId,
                             boolean processing,
                             String mode,
                             String action,
                             String error,
                             String source,
                             Double score,
                             String suggestedReply,
                             String decisionType,
                             String decisionReason,
                             String sourceHits) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, last_source, last_score, last_suggested_reply, decision_type, decision_reason, source_hits, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = excluded.is_processing,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        last_source = excluded.last_source,
                        last_score = excluded.last_score,
                        last_suggested_reply = excluded.last_suggested_reply,
                        decision_type = excluded.decision_type,
                        decision_reason = excluded.decision_reason,
                        source_hits = excluded.source_hits,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    ticket,
                    processing ? 1 : 0,
                    persistenceService.trim(mode),
                    persistenceService.trim(action),
                    persistenceService.trim(error),
                    persistenceService.trim(source),
                    score,
                    persistenceService.trim(suggestedReply),
                    persistenceService.trim(decisionType),
                    persistenceService.trim(decisionReason),
                    persistenceService.trim(sourceHits)
            );
        } catch (Exception schemaMismatch) {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, last_source, last_score, last_suggested_reply, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = excluded.is_processing,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        last_source = excluded.last_source,
                        last_score = excluded.last_score,
                        last_suggested_reply = excluded.last_suggested_reply,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    ticket,
                    processing ? 1 : 0,
                    persistenceService.trim(mode),
                    persistenceService.trim(action),
                    persistenceService.trim(error),
                    persistenceService.trim(source),
                    score,
                    persistenceService.trim(suggestedReply)
            );
        }
    }

    public record DialogAiControl(boolean aiDisabled,
                                  boolean autoReplyBlocked,
                                  String reason,
                                  String updatedBy,
                                  String updatedAt) {
        static final DialogAiControl DEFAULT = new DialogAiControl(false, false, null, null, null);
    }
}
