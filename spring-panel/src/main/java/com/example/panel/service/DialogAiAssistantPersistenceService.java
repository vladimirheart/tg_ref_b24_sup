package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DialogAiAssistantPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantPersistenceService.class);
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_AUTO_REPLY = "auto_reply";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final AiPolicyService aiPolicyService;
    private final AiKnowledgeService aiKnowledgeService;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantPersistenceService(JdbcTemplate jdbcTemplate,
                                               SharedConfigService sharedConfigService,
                                               AiPolicyService aiPolicyService,
                                               AiKnowledgeService aiKnowledgeService,
                                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.aiPolicyService = aiPolicyService;
        this.aiKnowledgeService = aiKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    public Long toLong(Object value) {
        try {
            if (value == null) {
                return null;
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    public String cut(String text, int len) {
        String trimmed = trim(text);
        if (trimmed == null) {
            return null;
        }
        String compact = trimmed.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    public String buildKey(String question) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalize(question).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(normalize(question).hashCode());
        }
    }

    public String loadLastClientMessage(String ticketId) {
        return jdbcTemplate.query(
                "SELECT message FROM chat_history WHERE ticket_id=? AND lower(sender) NOT IN ('operator','support','admin','system','ai_agent') AND message IS NOT NULL AND trim(message)<>'' ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? trim(rs.getString("message")) : null,
                ticketId
        );
    }

    public Map<String, Object> loadSolutionMemoryByKey(String queryKey) {
        String key = trim(queryKey);
        if (key == null) {
            return null;
        }
        try {
            java.util.List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT query_text, solution_text, review_required FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    key
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    public void insertSolutionMemoryHistory(String queryKey,
                                            String changedBy,
                                            String changeSource,
                                            String changeAction,
                                            String oldQueryText,
                                            String oldSolutionText,
                                            boolean oldReviewRequired,
                                            String newQueryText,
                                            String newSolutionText,
                                            boolean newReviewRequired,
                                            String note) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_solution_memory_history(
                        query_key, changed_by, change_source, change_action,
                        old_query_text, old_solution_text, old_review_required,
                        new_query_text, new_solution_text, new_review_required, note, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(queryKey),
                    trim(changedBy),
                    cut(trim(changeSource), 64),
                    cut(trim(changeAction), 64),
                    cut(trim(oldQueryText), 600),
                    cut(trim(oldSolutionText), 2000),
                    oldReviewRequired ? 1 : 0,
                    cut(trim(newQueryText), 600),
                    cut(trim(newSolutionText), 2000),
                    newReviewRequired ? 1 : 0,
                    cut(trim(note), 500)
            );
        } catch (Exception ex) {
            log.debug("Failed to insert ai solution memory history for {}: {}", queryKey, ex.getMessage());
        }
    }

    public void applyMemoryGovernance(String queryKey,
                                      String status,
                                      String trustLevel,
                                      String intentKey,
                                      String slotSignature,
                                      String scopeChannel,
                                      String safetyLevel,
                                      String sourceType,
                                      boolean verified,
                                      String verifiedBy) {
        String key = trim(queryKey);
        if (key == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET status = ?,
                           trust_level = ?,
                           intent_key = ?,
                           slot_signature = ?,
                           scope_channel = COALESCE(scope_channel, ?),
                           safety_level = ?,
                           source_type = ?,
                           last_verified_at = CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP ELSE last_verified_at END,
                           verified_by = CASE WHEN ? = 1 THEN ? ELSE verified_by END,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    aiPolicyService.normalizeStatus(status, "draft"),
                    aiPolicyService.normalizeTrustLevel(trustLevel, "low"),
                    cut(trim(intentKey), 120),
                    cut(trim(slotSignature), 300),
                    cut(trim(scopeChannel), 120),
                    aiPolicyService.normalizeSafetyLevel(safetyLevel, "normal"),
                    aiPolicyService.normalizeSourceType("memory", sourceType),
                    verified ? 1 : 0,
                    verified ? 1 : 0,
                    cut(trim(verifiedBy), 120),
                    key
            );
        } catch (Exception ignored) {
            // Backward compatibility for databases where governance columns are not present yet.
        }
    }

    public void clearProcessing(String ticketId, String action, String error) {
        String ticket = trim(ticketId);
        if (ticket == null) {
            return;
        }
        String mode = resolveAgentMode();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, updated_at)
                    VALUES (?, 0, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = 0,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    ticket,
                    trim(mode),
                    trim(action),
                    trim(error)
            );
        } catch (Exception ex) {
            log.debug("Failed to clear ai processing state for {}: {}", ticket, ex.getMessage());
        }
    }

    public void recordAiEvent(String ticketId,
                              String eventType,
                              String actor,
                              String decisionType,
                              String decisionReason,
                              String source,
                              Double score,
                              String detail,
                              Map<String, Object> payload) {
        String type = trim(eventType);
        if (type == null) {
            return;
        }
        String policyStage = payload != null ? trim(safe(payload.get("policy_stage"))) : null;
        String policyOutcome = payload != null ? trim(safe(payload.get("policy_outcome"))) : null;
        String intentKey = payload != null ? trim(safe(payload.get("intent_key"))) : null;
        Integer sensitiveTopic = payload != null && isTrue(payload.get("sensitive_topic")) ? 1 : 0;
        String topCandidateTrust = payload != null ? trim(safe(payload.get("top_candidate_trust"))) : null;
        String topCandidateSourceType = payload != null ? trim(safe(payload.get("top_candidate_source_type"))) : null;
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_event_log(
                        ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json,
                        policy_stage, policy_outcome, intent_key, sensitive_topic, top_candidate_trust, top_candidate_source_type, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(ticketId),
                    cut(type, 80),
                    trim(actor),
                    trim(decisionType),
                    trim(decisionReason),
                    trim(source),
                    score,
                    cut(detail, 2000),
                    payload != null && !payload.isEmpty() ? cut(toJson(payload), 5000) : null,
                    cut(policyStage, 80),
                    cut(policyOutcome, 80),
                    cut(intentKey, 120),
                    sensitiveTopic,
                    cut(topCandidateTrust, 32),
                    cut(topCandidateSourceType, 64)
            );
            return;
        } catch (Exception ignored) {
            // Backward compatibility for pre-migration schema.
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_event_log(
                        ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(ticketId),
                    cut(type, 80),
                    trim(actor),
                    trim(decisionType),
                    trim(decisionReason),
                    trim(source),
                    score,
                    cut(detail, 2000),
                    payload != null && !payload.isEmpty() ? cut(toJson(payload), 5000) : null
            );
        } catch (Exception ex) {
            log.debug("Failed to record ai event '{}' for ticket {}: {}", type, ticketId, ex.getMessage());
        }
    }

    public void syncFromMemory(String queryKey) {
        aiKnowledgeService.syncFromMemory(queryKey);
    }

    public void forgetMemory(String queryKey) {
        aiKnowledgeService.forgetMemory(queryKey);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveAgentMode() {
        return resolveDialogConfigString(
                "ai_agent_mode",
                MODE_ASSIST_ONLY,
                Set.of(MODE_AUTO_REPLY, MODE_ASSIST_ONLY, MODE_ESCALATE_ONLY)
        );
    }

    private String resolveDialogConfigString(String key, String fallback, Set<String> allowedValues) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) {
                return fallback;
            }
            Object rawValue = map.get(key);
            if (rawValue == null) {
                return fallback;
            }
            String value = trim(String.valueOf(rawValue));
            if (value == null) {
                return fallback;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            return allowedValues.contains(normalized) ? normalized : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    public Instant parseInstant(Object value) {
        String raw = trim(value != null ? String.valueOf(value) : null);
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String normalized = raw.replace(' ', 'T');
            if (normalized.length() == 19) {
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
}
