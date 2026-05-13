package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantEventService {

    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantEventService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantEventService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String encodeSourceHits(List<DialogAiAssistantSuggestionCandidate> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (DialogAiAssistantSuggestionCandidate suggestion : suggestions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", suggestion.source());
            row.put("title", suggestion.title());
            row.put("score", Math.round(Math.max(0d, Math.min(1d, suggestion.score())) * 1000d) / 1000d);
            row.put("memory_key", suggestion.memoryKey());
            row.put("status", suggestion.status());
            row.put("trust_level", suggestion.trustLevel());
            row.put("source_type", suggestion.sourceType());
            row.put("safety_level", suggestion.safetyLevel());
            row.put("source_ref", suggestion.sourceRef());
            row.put("intent_key", suggestion.intentKey());
            row.put("slot_signature", suggestion.slotSignature());
            row.put("canonical_key", suggestion.canonicalKey());
            row.put("evidence_count", suggestion.evidenceCount());
            row.put("trace", suggestion.trace());
            row.put("updated_at", suggestion.updatedAt());
            row.put("stale", suggestion.stale() ? 1 : 0);
            payload.add(row);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public Map<String, Object> buildRetrievalPayload(String sourceHits,
                                                     DialogAiAssistantSuggestionCandidate top,
                                                     AiRetrievalService.RetrievalResult retrievalResult,
                                                     boolean sensitiveTopic,
                                                     AiControlledLlmService.RewriteResult rewriteResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_hits", sourceHits);
        payload.put("sensitive_topic", sensitiveTopic ? 1 : 0);
        if (rewriteResult != null) {
            payload.put("llm_rewrite_used", rewriteResult.usedLlm() ? 1 : 0);
            payload.put("llm_rewrite_reason", rewriteResult.reason());
            payload.put("llm_rewrite_query", rewriteResult.rewrittenQuery());
            payload.put("llm_variant", rewriteResult.variant());
        }
        if (retrievalResult != null) {
            payload.put("support_count", retrievalResult.consistency().supportCount());
            payload.put("evidence_conflict", retrievalResult.consistency().hasConflict() ? 1 : 0);
            payload.put("retrieval_consistency_reason", retrievalResult.consistency().reason());
            if (retrievalResult.context() != null && retrievalResult.context().intentMatch() != null) {
                String intentKey = trim(retrievalResult.context().intentMatch().intentKey());
                if (intentKey != null) {
                    payload.put("intent_key", intentKey);
                }
                payload.put("intent_confidence", retrievalResult.context().intentMatch().confidence());
                payload.put("intent_schema_valid", retrievalResult.context().intentMatch().schemaValid() ? 1 : 0);
            }
        }
        if (top != null) {
            payload.put("top_candidate_trust", top.trustLevel());
            payload.put("top_candidate_source_type", top.sourceType());
            payload.put("top_candidate_source_ref", top.sourceRef());
            payload.put("top_candidate_intent", top.intentKey());
            payload.put("top_candidate_slot_signature", top.slotSignature());
            payload.put("top_candidate_canonical_key", top.canonicalKey());
            payload.put("top_candidate_evidence_count", top.evidenceCount());
            payload.put("top_candidate_trace", top.trace());
            payload.put("top_candidate_updated_at", top.updatedAt());
            payload.put("top_candidate_is_stale", top.stale() ? 1 : 0);
        }
        return payload;
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
            // Fallback for pre-migration schema.
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

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String cut(String text, int len) {
        String trimmed = trim(text);
        if (trimmed == null) {
            return null;
        }
        String compact = trimmed.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }
}
