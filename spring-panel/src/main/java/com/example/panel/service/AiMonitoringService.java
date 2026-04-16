package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiMonitoringService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiOfflineEvaluationService aiOfflineEvaluationService;

    public AiMonitoringService(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               AiOfflineEvaluationService aiOfflineEvaluationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiOfflineEvaluationService = aiOfflineEvaluationService;
    }

    public Map<String, Object> loadMonitoringSummary(Integer days) {
        int safeDays = Math.max(1, Math.min(days != null ? days : 7, 90));
        String sinceExpr = "-" + safeDays + " days";
        String previousSinceExpr = "-" + (safeDays * 2) + " days";

        int inboundMessages = queryCount(
                "SELECT COUNT(*) FROM chat_history WHERE lower(COALESCE(sender,'')) NOT IN ('operator','support','admin','system','ai_agent') AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int autoReplies = queryCount(
                "SELECT COUNT(*) FROM chat_history WHERE lower(COALESCE(sender,'')) = 'ai_agent' AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int suggestOnly = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(last_action,'')) = 'suggest_only' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int escalations = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(decision_type,'')) = 'escalate' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int operatorCorrections = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(last_action,'')) = 'operator_correction_requested' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int rejectedSuggestions = queryCount(
                "SELECT COUNT(*) FROM ai_agent_suggestion_feedback WHERE lower(COALESCE(decision,'')) = 'rejected' AND datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int acceptedSuggestions = queryCount(
                "SELECT COUNT(*) FROM ai_agent_suggestion_feedback WHERE lower(COALESCE(decision,'')) = 'accepted' AND datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );

        List<Map<String, Object>> recentEvents = loadMonitoringEventsWithColumns(sinceExpr, 1500, null, null, null, true);
        RuntimeMetrics runtimeMetrics = buildRuntimeMetrics(recentEvents);
        ReviewQueueMetrics reviewQueueMetrics = buildReviewQueueMetrics(safeDays, sinceExpr, previousSinceExpr);

        double autoReplyRate = safeRate(autoReplies, inboundMessages);
        double assistUsageRate = safeRate(suggestOnly, inboundMessages);
        double escalationRate = safeRate(escalations, inboundMessages);
        double correctionRate = safeRate(operatorCorrections, Math.max(1, autoReplies + suggestOnly));
        double rejectionRate = safeRate(rejectedSuggestions, Math.max(1, rejectedSuggestions + acceptedSuggestions));

        List<Map<String, Object>> alerts = buildMonitoringAlerts(
                inboundMessages,
                autoReplies,
                suggestOnly,
                escalations,
                operatorCorrections,
                rejectedSuggestions,
                autoReplyRate,
                escalationRate,
                correctionRate,
                rejectionRate,
                runtimeMetrics,
                reviewQueueMetrics
        );

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("inbound_messages", inboundMessages);
        kpis.put("auto_replies", autoReplies);
        kpis.put("suggest_only", suggestOnly);
        kpis.put("escalations", escalations);
        kpis.put("operator_corrections", operatorCorrections);
        kpis.put("rejected_suggestions", rejectedSuggestions);
        kpis.put("accepted_suggestions", acceptedSuggestions);
        kpis.put("auto_reply_rate", autoReplyRate);
        kpis.put("assist_usage_rate", assistUsageRate);
        kpis.put("escalation_rate", escalationRate);
        kpis.put("correction_rate", correctionRate);
        kpis.put("suggestion_rejection_rate", rejectionRate);
        kpis.put("wrong_auto_reply_rate", runtimeMetrics.wrongAutoReplyRate());
        kpis.put("auto_reply_from_untrusted_source_rate", runtimeMetrics.autoReplyFromUntrustedSourceRate());
        kpis.put("sensitive_topic_block_rate", runtimeMetrics.sensitiveTopicBlockRate());
        kpis.put("approved_memory_reuse_rate", runtimeMetrics.approvedMemoryReuseRate());
        kpis.put("stale_memory_hit_rate", runtimeMetrics.staleMemoryHitRate());
        kpis.put("review_queue_age_p95", reviewQueueMetrics.reviewQueueAgeP95Hours());
        kpis.put("review_queue_growth_rate", reviewQueueMetrics.reviewQueueGrowthRate());

        Map<String, Object> runbook = new LinkedHashMap<>();
        runbook.put("title", "AI Agent Incident Runbook");
        runbook.put("items", List.of(
                "Проверьте escalation_rate, correction_rate и wrong_auto_reply_rate.",
                "Если sensitive_topic_block_rate падает, проверьте policy и guard на high-risk intent’ах.",
                "Если stale_memory_hit_rate растет, пересмотрите TTL и процесс ревью подтвержденной памяти.",
                "Если review_queue_growth_rate стабильно положительный, временно понизьте rollout до assist_only."
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", safeDays);
        payload.put("generated_at", Instant.now().toString());
        payload.put("kpis", kpis);
        payload.put("alerts", alerts);
        payload.put("runbook", runbook);
        payload.put("offline_eval", aiOfflineEvaluationService.loadLatestRun());
        return payload;
    }

    public List<Map<String, Object>> loadMonitoringEvents(Integer days,
                                                          Integer limit,
                                                          String ticketId,
                                                          String eventType,
                                                          String actor) {
        int safeDays = Math.max(1, Math.min(days != null ? days : 7, 90));
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));
        String sinceExpr = "-" + safeDays + " days";
        String ticket = trim(ticketId);
        String event = trim(eventType);
        String who = trim(actor);
        try {
            return loadMonitoringEventsWithColumns(sinceExpr, safeLimit, ticket, event, who, true);
        } catch (Exception fallback) {
            try {
                return loadMonitoringEventsWithColumns(sinceExpr, safeLimit, ticket, event, who, false);
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    private List<Map<String, Object>> loadMonitoringEventsWithColumns(String sinceExpr,
                                                                      int safeLimit,
                                                                      String ticket,
                                                                      String event,
                                                                      String who,
                                                                      boolean extendedColumns) {
        List<Object> params = new ArrayList<>();
        String selectColumns = extendedColumns
                ? "id, ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json, policy_stage, policy_outcome, intent_key, sensitive_topic, top_candidate_trust, top_candidate_source_type, created_at"
                : "id, ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json, created_at";
        StringBuilder sql = new StringBuilder("""
                SELECT %s
                  FROM ai_agent_event_log
                 WHERE datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)
                """.formatted(selectColumns));
        params.add(sinceExpr);
        if (ticket != null) {
            sql.append(" AND ticket_id = ?");
            params.add(ticket);
        }
        if (event != null) {
            sql.append(" AND lower(COALESCE(event_type,'')) = ?");
            params.add(event.toLowerCase(Locale.ROOT));
        }
        if (who != null) {
            sql.append(" AND lower(COALESCE(actor,'')) = ?");
            params.add(who.toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(safeLimit);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private RuntimeMetrics buildRuntimeMetrics(List<Map<String, Object>> events) {
        int autoReplyDecisions = 0;
        int wrongAutoReplies = 0;
        int untrustedAutoReplies = 0;
        int sensitiveEvents = 0;
        int sensitiveBlocked = 0;
        int decisionEvents = 0;
        int approvedMemoryReuseHits = 0;
        int memoryHits = 0;
        int staleMemoryHits = 0;

        for (Map<String, Object> event : events) {
            String eventType = normalize(stringValue(event.get("event_type")));
            Map<String, Object> payload = parseJson(event.get("payload_json"));
            boolean sensitive = isTrue(event.get("sensitive_topic")) || isTrue(payload.get("sensitive_topic"));
            if (sensitive) {
                sensitiveEvents++;
                String policyOutcome = normalize(stringValue(event.get("policy_outcome")));
                if (policyOutcome.contains("blocked") || "ai_agent_escalated".equals(eventType)) {
                    sensitiveBlocked++;
                }
            }
            if ("ai_agent_auto_reply_sent".equals(eventType)) {
                autoReplyDecisions++;
                String trust = normalize(stringValue(event.get("top_candidate_trust")));
                String sourceType = normalize(stringValue(event.get("top_candidate_source_type")));
                if (!"medium".equals(trust) && !"high".equals(trust)) {
                    untrustedAutoReplies++;
                }
                if ("history".equals(sourceType) || "applicant_history".equals(sourceType) || "unknown".equals(sourceType)) {
                    untrustedAutoReplies++;
                }
            }
            if ("ai_agent_correction_requested".equals(eventType)) {
                wrongAutoReplies++;
            }
            if ("ai_agent_auto_reply_sent".equals(eventType) || "ai_agent_suggestion_shown".equals(eventType)) {
                decisionEvents++;
                String source = normalize(stringValue(event.get("source")));
                String trust = normalize(stringValue(event.get("top_candidate_trust")));
                if ("memory".equals(source)) {
                    memoryHits++;
                    if ("medium".equals(trust) || "high".equals(trust)) {
                        approvedMemoryReuseHits++;
                    }
                    if (isTrue(payload.get("top_candidate_is_stale"))) {
                        staleMemoryHits++;
                    }
                }
            }
        }

        return new RuntimeMetrics(
                safeRate(wrongAutoReplies, autoReplyDecisions),
                safeRate(untrustedAutoReplies, autoReplyDecisions),
                safeRate(sensitiveBlocked, sensitiveEvents),
                safeRate(approvedMemoryReuseHits, decisionEvents),
                safeRate(staleMemoryHits, Math.max(1, memoryHits))
        );
    }

    private ReviewQueueMetrics buildReviewQueueMetrics(int windowDays, String sinceExpr, String previousSinceExpr) {
        List<Double> agesHours = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT updated_at, created_at
                      FROM ai_agent_solution_memory
                     WHERE COALESCE(review_required,0) = 1
                       AND trim(COALESCE(pending_solution_text,'')) <> ''
                    """
            );
            Instant now = Instant.now();
            for (Map<String, Object> row : rows) {
                Instant timestamp = parseInstant(firstNonBlank(stringValue(row.get("updated_at")), stringValue(row.get("created_at"))));
                if (timestamp != null) {
                    agesHours.add(Duration.between(timestamp, now).toMinutes() / 60d);
                }
            }
        } catch (Exception ignored) {
        }
        agesHours.sort(Double::compareTo);
        double p95 = percentile(agesHours, 0.95d);
        int currentQueue = queryCount(
                """
                SELECT COUNT(*)
                  FROM ai_agent_solution_memory
                 WHERE COALESCE(review_required,0) = 1
                   AND trim(COALESCE(pending_solution_text,'')) <> ''
                   AND datetime(substr(COALESCE(updated_at, created_at, ''),1,19)) >= datetime('now', ?)
                """,
                sinceExpr
        );
        int previousQueue = queryCount(
                """
                SELECT COUNT(*)
                  FROM ai_agent_solution_memory
                 WHERE COALESCE(review_required,0) = 1
                   AND trim(COALESCE(pending_solution_text,'')) <> ''
                   AND datetime(substr(COALESCE(updated_at, created_at, ''),1,19)) >= datetime('now', ?)
                   AND datetime(substr(COALESCE(updated_at, created_at, ''),1,19)) < datetime('now', ?)
                """,
                previousSinceExpr,
                sinceExpr
        );
        double growthRate = (currentQueue - previousQueue) / (double) Math.max(1, previousQueue);
        return new ReviewQueueMetrics(Math.round(p95 * 100d) / 100d, Math.round(growthRate * 10000d) / 10000d, windowDays);
    }

    private List<Map<String, Object>> buildMonitoringAlerts(int inboundMessages,
                                                            int autoReplies,
                                                            int suggestOnly,
                                                            int escalations,
                                                            int operatorCorrections,
                                                            int rejectedSuggestions,
                                                            double autoReplyRate,
                                                            double escalationRate,
                                                            double correctionRate,
                                                            double rejectionRate,
                                                            RuntimeMetrics runtimeMetrics,
                                                            ReviewQueueMetrics reviewQueueMetrics) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (inboundMessages == 0) {
            alerts.add(alert("info", "Нет входящих сообщений в выбранном окне.", 0d, 1d));
            return alerts;
        }
        if (escalations >= 5 && escalationRate > 0.35d) {
            alerts.add(alert("warning", "Высокий escalation rate: чаще подключайте оператора и проверяйте источники.", escalationRate, 0.35d));
        }
        if (operatorCorrections >= 3 && correctionRate > 0.25d) {
            alerts.add(alert("warning", "Высокий correction rate: AI-ответы часто требуют правок оператора.", correctionRate, 0.25d));
        }
        if (rejectedSuggestions >= 5 && rejectionRate > 0.40d) {
            alerts.add(alert("warning", "Операторы часто отклоняют подсказки AI.", rejectionRate, 0.40d));
        }
        if ((autoReplies + suggestOnly) >= 10 && autoReplyRate < 0.05d) {
            alerts.add(alert("info", "Низкий auto-reply rate: проверьте пороги и качество retrieval.", autoReplyRate, 0.05d));
        }
        if (runtimeMetrics.wrongAutoReplyRate() > 0.12d) {
            alerts.add(alert("warning", "Wrong auto-reply rate выше безопасного порога.", runtimeMetrics.wrongAutoReplyRate(), 0.12d));
        }
        if (runtimeMetrics.autoReplyFromUntrustedSourceRate() > 0d) {
            alerts.add(alert("warning", "Обнаружены auto-reply из недоверенного источника или trust ниже medium.", runtimeMetrics.autoReplyFromUntrustedSourceRate(), 0d));
        }
        if (runtimeMetrics.staleMemoryHitRate() > 0.20d) {
            alerts.add(alert("warning", "Stale memory hit rate высокий: проверьте TTL и ревью подтвержденной памяти.", runtimeMetrics.staleMemoryHitRate(), 0.20d));
        }
        if (reviewQueueMetrics.reviewQueueGrowthRate() > 0.15d) {
            alerts.add(alert("warning", "Очередь ревью растет быстрее ожидаемого.", reviewQueueMetrics.reviewQueueGrowthRate(), 0.15d));
        }
        if (alerts.isEmpty()) {
            alerts.add(alert("ok", "Показатели стабильны, критичных отклонений не обнаружено.", 0d, 0d));
        }
        return alerts;
    }

    private int queryCount(String sql, Object... params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class, params);
            return value != null ? Math.max(0, value) : 0;
        } catch (Exception ex) {
            return 0;
        }
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

    private double percentile(List<Double> items, double percentile) {
        if (items == null || items.isEmpty()) {
            return 0d;
        }
        int index = (int) Math.ceil(percentile * items.size()) - 1;
        int safeIndex = Math.max(0, Math.min(items.size() - 1, index));
        return items.get(safeIndex);
    }

    private Map<String, Object> alert(String severity, String message, double value, double threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("severity", severity);
        item.put("message", message);
        item.put("value", Math.round(value * 10000d) / 10000d);
        item.put("threshold", Math.round(threshold * 10000d) / 10000d);
        return item;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean rawBoolean) {
            return rawBoolean;
        }
        String normalized = normalize(stringValue(value));
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private double safeRate(int numerator, int denominator) {
        if (denominator <= 0) return 0d;
        return Math.max(0d, Math.min(1d, numerator / (double) denominator));
    }

    private Instant parseInstant(String rawValue) {
        String raw = trim(rawValue);
        if (raw == null) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        return null;
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

    private record RuntimeMetrics(double wrongAutoReplyRate,
                                  double autoReplyFromUntrustedSourceRate,
                                  double sensitiveTopicBlockRate,
                                  double approvedMemoryReuseRate,
                                  double staleMemoryHitRate) {
    }

    private record ReviewQueueMetrics(double reviewQueueAgeP95Hours,
                                      double reviewQueueGrowthRate,
                                      int windowDays) {
    }
}
