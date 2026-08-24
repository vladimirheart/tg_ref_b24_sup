package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.panel.support.PanelTimestampSqlSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiOpsRuntimeService {

    private static final Set<String> ALLOWED_AGENT_MODES = Set.of("auto_reply", "assist_only", "escalate_only");

    private final JdbcTemplate jdbcTemplate;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final AiIntentService aiIntentService;
    private final AiRetrievalService aiRetrievalService;
    private final AiPolicyService aiPolicyService;
    private final AiDecisionService aiDecisionService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantPolicyService dialogAiAssistantPolicyService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiControlledLlmService aiControlledLlmService;
    private final AiOfflineEvaluationService aiOfflineEvaluationService;
    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;
    private final PanelTimestampSqlSupport timestampSqlSupport;

    public AiOpsRuntimeService(JdbcTemplate jdbcTemplate,
                               DialogAiAssistantService dialogAiAssistantService,
                               AiIntentService aiIntentService,
                               AiRetrievalService aiRetrievalService,
                               AiPolicyService aiPolicyService,
                               AiDecisionService aiDecisionService,
                               DialogAiAssistantConfigService dialogAiAssistantConfigService,
                               DialogAiAssistantStateService dialogAiAssistantStateService,
                               DialogAiAssistantPolicyService dialogAiAssistantPolicyService,
                               AiInputNormalizerService aiInputNormalizerService,
                               AiControlledLlmService aiControlledLlmService,
                               AiOfflineEvaluationService aiOfflineEvaluationService,
                               ObjectMapper objectMapper,
                               SharedConfigService sharedConfigService,
                               PanelTimestampSqlSupport timestampSqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.aiIntentService = aiIntentService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiPolicyService = aiPolicyService;
        this.aiDecisionService = aiDecisionService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantPolicyService = dialogAiAssistantPolicyService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiControlledLlmService = aiControlledLlmService;
        this.aiOfflineEvaluationService = aiOfflineEvaluationService;
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
        this.timestampSqlSupport = timestampSqlSupport;
    }

    public Map<String, Object> loadDecisionTrace(String ticketId, Integer limit) {
        String ticket = trim(ticketId);
        int safeLimit = Math.max(5, Math.min(limit != null ? limit : 20, 100));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", ticket);
        payload.put("last_client", loadLastClientPayload(ticket));
        payload.put("state", loadState(ticket));
        payload.put("pending_review", dialogAiAssistantService.loadPendingReview(ticket));
        payload.put("events", loadEventTrace(ticket, safeLimit));
        payload.put("offline_eval_latest", aiOfflineEvaluationService.loadLatestRun());
        return payload;
    }

    public Map<String, Object> reclassify(String ticketId,
                                          String message,
                                          String messageType,
                                          String attachment) {
        String ticket = trim(ticketId);
        AiInputNormalizerService.IncomingPayload payload = resolveIncomingPayload(ticket, message, messageType, attachment);
        String normalizedMessage = payload != null ? payload.message() : trim(message);
        AiIntentService.IntentMatch intentMatch = aiIntentService.extract(normalizedMessage);
        AiIntentService.IntentPolicy intentPolicy = aiIntentService.resolvePolicy(intentMatch.intentKey());
        AiPolicyService.SensitiveTopicMatch sensitiveTopicMatch = aiPolicyService.detectSensitiveTopic(normalizedMessage);
        AiControlledLlmService.IntentAnalysis llmIntent = aiControlledLlmService.analyzeIntent(ticket, normalizedMessage, intentMatch);
        String effectiveMode = applyIntentModeOverride(
                aiPolicyService.applySensitiveModeOverride(resolveAgentMode(), sensitiveTopicMatch),
                intentPolicy
        );
        return Map.of(
                "ticket_id", ticket,
                "message", normalizedMessage,
                "intent", intentToMap(intentMatch),
                "intent_policy", intentPolicyToMap(intentPolicy),
                "sensitive_topic", sensitiveTopicMatch.asPayload(),
                "effective_mode", effectiveMode,
                "llm_parser", llmIntent
        );
    }

    public Map<String, Object> retrieveDebug(String ticketId,
                                             String message,
                                             String messageType,
                                             String attachment,
                                             Integer limit) {
        String ticket = trim(ticketId);
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 5, 20));
        AiInputNormalizerService.IncomingPayload payload = resolveIncomingPayload(ticket, message, messageType, attachment);
        String normalizedMessage = payload != null ? payload.message() : trim(message);
        AiControlledLlmService.RewriteResult rewriteResult = aiControlledLlmService.rewriteQuery(ticket, normalizedMessage);
        String retrievalQuery = firstNonBlank(rewriteResult.effectiveQuery(), normalizedMessage);
        AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(ticket, retrievalQuery, safeLimit);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (AiRetrievalService.Candidate candidate : retrievalResult.candidates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", candidate.source());
            item.put("title", candidate.title());
            item.put("snippet", candidate.snippet());
            item.put("score", candidate.score());
            item.put("memory_key", candidate.memoryKey());
            item.put("status", candidate.status());
            item.put("trust_level", candidate.trustLevel());
            item.put("source_type", candidate.sourceType());
            item.put("safety_level", candidate.safetyLevel());
            item.put("source_ref", candidate.sourceRef());
            item.put("intent_key", candidate.intentKey());
            item.put("slot_signature", candidate.slotSignature());
            item.put("canonical_key", candidate.canonicalKey());
            item.put("evidence_count", candidate.evidenceCount());
            item.put("trace", candidate.trace());
            item.put("updated_at", candidate.updatedAt());
            item.put("stale", candidate.stale());
            item.put("memory_auto_reply_allowed",
                    "memory".equalsIgnoreCase(candidate.source())
                            && aiPolicyService.isMemoryAutoReplyAllowed(candidate.memoryKey()));
            candidates.add(item);
        }

        Map<String, Object> contextMap = new LinkedHashMap<>();
        contextMap.put("intent", intentToMap(retrievalResult.context().intentMatch()));
        contextMap.put("intent_policy", intentPolicyToMap(retrievalResult.context().intentPolicy()));
        contextMap.put("channel", retrievalResult.context().channel());
        contextMap.put("business", retrievalResult.context().business());
        contextMap.put("location", retrievalResult.context().location());

        Map<String, Object> consistencyMap = new LinkedHashMap<>();
        consistencyMap.put("auto_reply_allowed", retrievalResult.consistency().autoReplyAllowed());
        consistencyMap.put("has_conflict", retrievalResult.consistency().hasConflict());
        consistencyMap.put("support_count", retrievalResult.consistency().supportCount());
        consistencyMap.put("reason", retrievalResult.consistency().reason());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticket_id", ticket);
        result.put("message", normalizedMessage);
        result.put("rewrite", rewriteResult);
        result.put("context", contextMap);
        result.put("consistency", consistencyMap);
        result.put("candidates", candidates);
        result.put("decision_preview", buildDecisionPreview(ticket, normalizedMessage, retrievalResult));
        return result;
    }

    private Map<String, Object> buildDecisionPreview(String ticketId,
                                                     String message,
                                                     AiRetrievalService.RetrievalResult retrievalResult) {
        Map<String, Object> preview = new LinkedHashMap<>();
        DialogAiAssistantStateService.DialogAiControl control =
                dialogAiAssistantStateService.loadDialogControl(ticketId);
        String configuredMode = dialogAiAssistantConfigService.resolveAgentMode();
        boolean agentEnabled = dialogAiAssistantConfigService.isAgentEnabled();
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                dialogAiAssistantPolicyService.evaluatePreRoutingPolicy(
                        message,
                        configuredMode,
                        control,
                        agentEnabled
                );
        preview.put("configured_mode", configuredMode);
        preview.put("agent_enabled", agentEnabled);
        preview.put("dialog_ai_disabled", control.aiDisabled());
        preview.put("dialog_auto_reply_blocked", control.autoReplyBlocked());
        preview.put("pre_routing_action", preRouting.action());
        preview.put("pre_routing_reason", preRouting.decisionReason());

        if (preRouting.stopProcessing()) {
            preview.put("effective_mode", preRouting.effectiveMode());
            preview.put("base_action", preRouting.decisionType());
            preview.put("base_reason", preRouting.decisionReason());
            preview.put("final_action", preRouting.decisionType());
            preview.put("final_reason", preRouting.decisionReason());
            preview.put("source_auto_reply_eligible", false);
            return preview;
        }

        double suggestThreshold = dialogAiAssistantConfigService.resolveSuggestThreshold();
        double configuredAutoReplyThreshold = dialogAiAssistantConfigService.resolveAutoReplyThreshold();
        preview.put("suggest_threshold", suggestThreshold);
        preview.put("auto_reply_threshold", configuredAutoReplyThreshold);

        if (retrievalResult.candidates().isEmpty()) {
            String effectiveMode = applyIntentModeOverride(
                    preRouting.effectiveMode(),
                    retrievalResult.context().intentPolicy(),
                    false
            );
            preview.put("effective_mode", effectiveMode);
            preview.put("base_action", "escalate");
            preview.put("base_reason", "no_evidence");
            preview.put("final_action", "escalate");
            preview.put("final_reason", "no_evidence");
            preview.put("source_auto_reply_eligible", false);
            return preview;
        }

        AiRetrievalService.Candidate top = retrievalResult.candidates().get(0);
        AiIntentService.IntentPolicy intentPolicy = retrievalResult.context().intentPolicy();
        boolean isMemory = "memory".equalsIgnoreCase(top.source());
        boolean memoryExplicitlyAllowed = isMemory
                && aiPolicyService.isMemoryAutoReplyAllowed(top.memoryKey());
        boolean explicitMemoryIntentOverride = memoryExplicitlyAllowed
                && aiPolicyService.isExplicitMemoryAutoReplyIntentEligible(intentPolicy);
        String effectiveMode = applyIntentModeOverride(
                preRouting.effectiveMode(),
                intentPolicy,
                explicitMemoryIntentOverride
        );
        double effectiveAutoReplyThreshold = memoryExplicitlyAllowed
                ? suggestThreshold
                : configuredAutoReplyThreshold;
        boolean intentEligibleForAutoReply = intentPolicy.autoReplyAllowed() || explicitMemoryIntentOverride;
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source(),
                top.status(),
                top.trustLevel(),
                top.sourceType(),
                top.safetyLevel()
        ) && intentEligibleForAutoReply
                && !intentPolicy.requiresOperator()
                && (!isMemory || memoryExplicitlyAllowed);
        preview.put("effective_mode", effectiveMode);
        preview.put("top_source", top.source());
        preview.put("top_memory_key", top.memoryKey());
        preview.put("top_score", top.score());
        preview.put("memory_auto_reply_allowed", memoryExplicitlyAllowed);
        preview.put("memory_intent_override", explicitMemoryIntentOverride);
        preview.put("effective_auto_reply_threshold", effectiveAutoReplyThreshold);
        preview.put("source_auto_reply_eligible", sourceEligibleForAutoReply);

        AiDecisionService.Decision baseDecision = aiDecisionService.evaluateCandidateDecision(
                effectiveMode,
                top.score(),
                suggestThreshold,
                effectiveAutoReplyThreshold,
                control.autoReplyBlocked(),
                sourceEligibleForAutoReply
        );
        String finalAction = baseDecision.decisionType();
        String finalReason = baseDecision.decisionReason();
        preview.put("base_action", baseDecision.decisionType());
        preview.put("base_reason", baseDecision.decisionReason());

        if (baseDecision.action() == AiDecisionService.DecisionAction.AUTO_REPLY) {
            AiRetrievalService.ConsistencyCheck consistency = retrievalResult.consistency();
            boolean explicitMemorySingleEvidenceAllowed = memoryExplicitlyAllowed
                    && !consistency.hasConflict()
                    && "insufficient_confirmations".equals(consistency.reason());
            if (!consistency.autoReplyAllowed() && !explicitMemorySingleEvidenceAllowed) {
                finalAction = consistency.hasConflict() ? "escalate" : "suggest_only";
                finalReason = consistency.reason();
            } else {
                DialogAiAssistantConfigService.AutoReplyGuard guard =
                        dialogAiAssistantConfigService.evaluateAutoReplyGuard(ticketId);
                preview.put("loop_guard_allowed", guard.allowed());
                preview.put("loop_guard_reason", guard.reason());
                if (!guard.allowed()) {
                    finalAction = "suppressed";
                    finalReason = guard.reason();
                }
            }
        }
        preview.put("final_action", finalAction);
        preview.put("final_reason", finalReason);
        return preview;
    }

    public List<Map<String, Object>> listIntents(Integer limit, String query, Boolean enabled) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 200, 500));
        String filter = trim(query);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT c.intent_key, c.title, c.description, c.pattern_hints, c.slot_schema_json, c.enabled, c.priority,
                       p.auto_reply_allowed, p.assist_only, p.requires_operator, p.safety_level, p.notes
                  FROM ai_agent_intent_catalog c
                  LEFT JOIN ai_agent_intent_policy p ON p.intent_key = c.intent_key
                 WHERE 1 = 1
                """);
        if (filter != null) {
            sql.append(" AND (lower(COALESCE(c.intent_key,'')) LIKE ? OR lower(COALESCE(c.title,'')) LIKE ?)");
            String like = "%" + filter.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            sql.append(" AND COALESCE(c.enabled, 0) = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" ORDER BY c.priority ASC, c.intent_key ASC LIMIT ?");
        params.add(safeLimit);
        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean upsertIntent(String intentKey,
                                String title,
                                String description,
                                String patternHints,
                                String slotSchemaJson,
                                Boolean enabled,
                                Integer priority,
                                Boolean autoReplyAllowed,
                                Boolean assistOnly,
                                Boolean requiresOperator,
                                String safetyLevel,
                                String notes) {
        String key = normalize(intentKey);
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT(intent_key) DO UPDATE SET
                        title = excluded.title,
                        description = excluded.description,
                        pattern_hints = excluded.pattern_hints,
                        slot_schema_json = excluded.slot_schema_json,
                        enabled = excluded.enabled,
                        priority = excluded.priority,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    key,
                    cut(title, 160),
                    cut(description, 500),
                    cut(patternHints, 2000),
                    cut(slotSchemaJson, 3000),
                    enabled != null && enabled ? 1 : 0,
                    priority != null ? priority : 500
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(intent_key) DO UPDATE SET
                        auto_reply_allowed = excluded.auto_reply_allowed,
                        assist_only = excluded.assist_only,
                        requires_operator = excluded.requires_operator,
                        safety_level = excluded.safety_level,
                        notes = excluded.notes
                    """,
                    key,
                    autoReplyAllowed != null && autoReplyAllowed ? 1 : 0,
                    assistOnly != null && assistOnly ? 1 : 0,
                    requiresOperator != null && requiresOperator ? 1 : 0,
                    cut(trim(safetyLevel), 32),
                    cut(notes, 1000)
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> listKnowledgeUnits(Integer limit, String query, String status) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 200, 500));
        String filter = trim(query);
        String normalizedStatus = trim(status);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT ku.id, ku.unit_key, ku.title, ku.body_text, ku.intent_key, ku.slot_signature,
                       ku.business, ku.location, ku.channel, ku.status, ku.source_ref, ku.created_at, ku.updated_at,
                       COALESCE((
                           SELECT COUNT(*)
                             FROM ai_agent_memory_link ml
                            WHERE ml.knowledge_unit_id = ku.id
                       ), 0) AS link_count
                  FROM ai_agent_knowledge_unit ku
                 WHERE 1 = 1
                """);
        if (filter != null) {
            sql.append(" AND (lower(COALESCE(ku.title,'')) LIKE ? OR lower(COALESCE(ku.body_text,'')) LIKE ? OR lower(COALESCE(ku.intent_key,'')) LIKE ?)");
            String like = "%" + filter.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (normalizedStatus != null) {
            sql.append(" AND lower(COALESCE(ku.status,'')) = ?");
            params.add(normalizedStatus.toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY ")
                .append(timestampSqlSupport.orderByTimestampDesc("COALESCE(ku.updated_at, ku.created_at)"))
                .append(", ku.id DESC LIMIT ?");
        params.add(safeLimit);
        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean upsertKnowledgeUnit(String unitKey,
                                       String title,
                                       String bodyText,
                                       String intentKey,
                                       String slotSignature,
                                       String business,
                                       String location,
                                       String channel,
                                       String status,
                                       String sourceRef) {
        String body = trim(bodyText);
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String normalizedIntent = normalize(intentKey);
        String key = firstNonBlank(trim(unitKey), buildKnowledgeKey(normalizedIntent, slotSignature, business, location, channel, body));
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_knowledge_unit(
                        unit_key, title, body_text, intent_key, slot_signature,
                        business, location, channel, status, source_ref, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT(unit_key) DO UPDATE SET
                        title = excluded.title,
                        body_text = excluded.body_text,
                        intent_key = excluded.intent_key,
                        slot_signature = excluded.slot_signature,
                        business = excluded.business,
                        location = excluded.location,
                        channel = excluded.channel,
                        status = excluded.status,
                        source_ref = excluded.source_ref,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    key,
                    cut(firstNonBlank(trim(title), normalizedIntent, "knowledge unit"), 200),
                    cut(body, 5000),
                    cut(normalizedIntent, 120),
                    cut(trim(slotSignature), 300),
                    cut(trim(business), 120),
                    cut(trim(location), 120),
                    cut(trim(channel), 120),
                    cut(firstNonBlank(trim(status), "active"), 32),
                    cut(trim(sourceRef), 160)
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Map<String, Object> loadOfflineEvalSummary() {
        Map<String, Object> payload = new LinkedHashMap<>(aiOfflineEvaluationService.loadLatestRun());
        payload.put("dataset", aiOfflineEvaluationService.loadDatasetOverview());
        return payload;
    }

    public Map<String, Object> runOfflineEvalNow(String actor) {
        Map<String, Object> payload = new LinkedHashMap<>(aiOfflineEvaluationService.runEvaluationNow(actor));
        payload.put("dataset", aiOfflineEvaluationService.loadDatasetOverview());
        return payload;
    }

    private Map<String, Object> loadLastClientPayload(String ticketId) {
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.loadLastClientPayload(ticketId);
        if (payload == null) {
            return Map.of();
        }
        return Map.of(
                "message", payload.message(),
                "message_type", payload.type(),
                "attachment", payload.attachment()
        );
    }

    private Map<String, Object> loadState(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT ticket_id, is_processing, mode, last_action, last_error, last_source, last_score,
                           last_suggested_reply, decision_type, decision_reason, source_hits, updated_at
                      FROM ticket_ai_agent_state
                     WHERE ticket_id = ?
                     LIMIT 1
                    """,
                    ticketId
            );
            if (rows.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
            row.put("source_hits_parsed", parseJson(row.get("source_hits")));
            return row;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> loadEventTrace(String ticketId, int limit) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT id, ticket_id, event_type, actor, decision_type, decision_reason, source, score,
                           detail, payload_json, policy_stage, policy_outcome, intent_key, sensitive_topic,
                           top_candidate_trust, top_candidate_source_type, created_at
                      FROM ai_agent_event_log
                     WHERE ticket_id = ?
                     ORDER BY id DESC
                     LIMIT ?
                    """,
                    ticketId,
                    limit
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                item.put("payload", parseJson(row.get("payload_json")));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private AiInputNormalizerService.IncomingPayload resolveIncomingPayload(String ticketId,
                                                                            String message,
                                                                            String messageType,
                                                                            String attachment) {
        if (StringUtils.hasText(message) || StringUtils.hasText(attachment)) {
            return aiInputNormalizerService.normalizeIncomingPayload(ticketId, message, messageType, attachment);
        }
        return aiInputNormalizerService.loadLastClientPayload(ticketId);
    }

    private Map<String, Object> intentToMap(AiIntentService.IntentMatch intentMatch) {
        return Map.of(
                "intent_key", intentMatch.intentKey(),
                "slots", intentMatch.slots(),
                "slots_json", intentMatch.slotsJson(),
                "slot_signature", intentMatch.slotSignature(),
                "schema_valid", intentMatch.schemaValid(),
                "required_missing", intentMatch.requiredMissing(),
                "confidence", intentMatch.confidence()
        );
    }

    private Map<String, Object> intentPolicyToMap(AiIntentService.IntentPolicy policy) {
        return Map.of(
                "intent_key", policy.intentKey(),
                "auto_reply_allowed", policy.autoReplyAllowed(),
                "assist_only", policy.assistOnly(),
                "requires_operator", policy.requiresOperator(),
                "safety_level", policy.safetyLevel(),
                "notes", policy.notes()
        );
    }

    private Map<String, Object> parseJson(Object rawValue) {
        try {
            String value = stringValue(rawValue);
            return StringUtils.hasText(value)
                    ? objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
                    })
                    : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String applyIntentModeOverride(String currentMode, AiIntentService.IntentPolicy intentPolicy) {
        return applyIntentModeOverride(currentMode, intentPolicy, false);
    }

    private String applyIntentModeOverride(String currentMode,
                                           AiIntentService.IntentPolicy intentPolicy,
                                           boolean explicitMemoryIntentOverride) {
        if (intentPolicy == null) {
            return currentMode;
        }
        if (intentPolicy.requiresOperator()) {
            return "escalate_only";
        }
        if (intentPolicy.assistOnly()
                && !explicitMemoryIntentOverride
                && !"escalate_only".equals(currentMode)) {
            return "assist_only";
        }
        return currentMode;
    }

    private String resolveAgentMode() {
        try {
            Object dialogConfigRaw = sharedConfigService.loadSettings().get("dialog_config");
            if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
                String value = normalize(stringValue(dialogConfig.get("ai_agent_mode")));
                return ALLOWED_AGENT_MODES.contains(value) ? value : "auto_reply";
            }
        } catch (Exception ignored) {
        }
        return "auto_reply";
    }

    private String buildKnowledgeKey(String intentKey,
                                     String slotSignature,
                                     String business,
                                     String location,
                                     String channel,
                                     String bodyText) {
        String seed = normalize(intentKey) + "|" + normalize(slotSignature) + "|" + normalize(business)
                + "|" + normalize(location) + "|" + normalize(channel) + "|" + normalize(bodyText);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private String cut(String value, int max) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= max ? normalized : normalized.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435').trim();
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
