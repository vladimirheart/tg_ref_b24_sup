package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiMonitoringService {

    private final JdbcTemplate jdbcTemplate;

    public AiMonitoringService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> loadMonitoringSummary(Integer days) {
        int safeDays = Math.max(1, Math.min(days != null ? days : 7, 90));
        String sinceExpr = "-" + safeDays + " days";

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
                rejectionRate
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

        Map<String, Object> runbook = new LinkedHashMap<>();
        runbook.put("title", "AI Agent Incident Runbook");
        runbook.put("items", List.of(
                "Проверьте значения escalation_rate и correction_rate.",
                "Если escalation_rate > 35%, временно переключите ai_agent_mode в assist_only.",
                "Если correction_rate > 25%, обновите базу знаний и скорректируйте шаблоны ответов.",
                "При массовых ошибках отключите AI для канала и назначьте срочный разбор."
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", safeDays);
        payload.put("generated_at", Instant.now().toString());
        payload.put("kpis", kpis);
        payload.put("alerts", alerts);
        payload.put("runbook", runbook);
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

    private int queryCount(String sql, Object... params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class, params);
            return value != null ? Math.max(0, value) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private double safeRate(int numerator, int denominator) {
        if (denominator <= 0) return 0d;
        return Math.max(0d, Math.min(1d, numerator / (double) denominator));
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
                                                            double rejectionRate) {
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
        if (alerts.isEmpty()) {
            alerts.add(alert("ok", "Показатели стабильны, критичных отклонений не обнаружено.", 0d, 0d));
        }
        return alerts;
    }

    private Map<String, Object> alert(String severity, String message, double value, double threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("severity", severity);
        item.put("message", message);
        item.put("value", Math.round(value * 10000d) / 10000d);
        item.put("threshold", Math.round(threshold * 10000d) / 10000d);
        return item;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

