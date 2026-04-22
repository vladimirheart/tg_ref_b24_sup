package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceTelemetryDataService {

    private static final Logger log = LoggerFactory.getLogger(DialogWorkspaceTelemetryDataService.class);

    private final JdbcTemplate jdbcTemplate;

    public DialogWorkspaceTelemetryDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> aggregateWorkspaceTelemetryRows(List<Map<String, Object>> rows, String dimension) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String operatorSegment = row != null ? trimOrNull((String) row.get("operator_segment")) : null;
            String key = resolveOperatorDimension(operatorSegment, dimension);
            Map<String, Object> bucket = aggregated.computeIfAbsent(key, ignored -> createTelemetryBucket(dimension, key));
            mergeTelemetryMetrics(bucket, row);
        }

        return aggregated.values().stream()
                .peek(bucket -> {
                    bucket.remove("_open_weight");
                    bucket.remove("_open_sum");
                })
                .sorted((left, right) -> Long.compare(toLong(right.get("events")), toLong(left.get("events"))))
                .toList();
    }

    public List<Map<String, Object>> loadWorkspaceTelemetryRows(Instant windowStart, Instant windowEnd, String experimentName) {
        String filterExperiment = trimOrNull(experimentName);
        String sql = """
                SELECT COALESCE(experiment_cohort, 'unknown') AS experiment_cohort,
                       COALESCE(operator_segment, 'unknown') AS operator_segment,
                       COUNT(*) AS events,
                       SUM(CASE WHEN event_type = 'workspace_render_error' THEN 1 ELSE 0 END) AS render_errors,
                       SUM(CASE WHEN event_type = 'workspace_fallback_to_legacy' THEN 1 ELSE 0 END) AS fallbacks,
                       SUM(CASE WHEN event_type = 'workspace_abandon' THEN 1 ELSE 0 END) AS abandons,
                       SUM(CASE WHEN event_type = 'workspace_open_ms' THEN 1 ELSE 0 END) AS workspace_open_events,
                       SUM(CASE WHEN event_type = 'workspace_context_profile_gap' THEN 1 ELSE 0 END) AS context_profile_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_source_gap' THEN 1 ELSE 0 END) AS context_source_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_attribute_policy_gap' THEN 1 ELSE 0 END) AS context_attribute_policy_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_block_gap' THEN 1 ELSE 0 END) AS context_block_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_contract_gap' THEN 1 ELSE 0 END) AS context_contract_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_sources_expanded' THEN 1 ELSE 0 END) AS context_sources_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_context_attribute_policy_expanded' THEN 1 ELSE 0 END) AS context_attribute_policy_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_context_extra_attributes_expanded' THEN 1 ELSE 0 END) AS context_extra_attributes_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_sla_policy_gap' THEN 1 ELSE 0 END) AS workspace_sla_policy_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_parity_gap' THEN 1 ELSE 0 END) AS workspace_parity_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_inline_navigation' THEN 1 ELSE 0 END) AS workspace_inline_navigation_events,
                       SUM(CASE WHEN event_type = 'workspace_open_legacy_manual' THEN 1 ELSE 0 END) AS manual_legacy_open_events,
                       SUM(CASE WHEN event_type = 'workspace_open_legacy_blocked' THEN 1 ELSE 0 END) AS workspace_open_legacy_blocked_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_packet_viewed' THEN 1 ELSE 0 END) AS workspace_rollout_packet_viewed_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_confirmed' THEN 1 ELSE 0 END) AS workspace_rollout_review_confirmed_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_go' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_go_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_hold' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_hold_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_rollback' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_rollback_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_incident_followup_linked' THEN 1 ELSE 0 END) AS workspace_rollout_review_incident_followup_linked_events,
                       SUM(CASE WHEN event_type = 'workspace_sla_policy_review_updated' THEN 1 ELSE 0 END) AS workspace_sla_policy_review_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_governance_review_updated' THEN 1 ELSE 0 END) AS workspace_macro_governance_review_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_external_catalog_policy_updated' THEN 1 ELSE 0 END) AS workspace_macro_external_catalog_policy_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_deprecation_policy_updated' THEN 1 ELSE 0 END) AS workspace_macro_deprecation_policy_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_legacy_usage_policy_updated' THEN 1 ELSE 0 END) AS workspace_legacy_usage_policy_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_open_ms' AND COALESCE(duration_ms, 0) > 2000 THEN 1 ELSE 0 END) AS slow_open_events,
                       SUM(CASE WHEN event_type = 'kpi_frt_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%frt%' THEN 1 ELSE 0 END) AS kpi_frt_events,
                       SUM(CASE WHEN event_type = 'kpi_ttr_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%ttr%' THEN 1 ELSE 0 END) AS kpi_ttr_events,
                       SUM(CASE WHEN event_type = 'kpi_sla_breach_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%sla_breach%' THEN 1 ELSE 0 END) AS kpi_sla_breach_events,
                       SUM(CASE WHEN event_type = 'kpi_dialogs_per_shift_recorded' OR LOWER(COALESCE(secondary_kpis, '')) LIKE '%dialogs_per_shift%' THEN 1 ELSE 0 END) AS kpi_dialogs_per_shift_events,
                       SUM(CASE WHEN event_type = 'kpi_csat_recorded' OR LOWER(COALESCE(secondary_kpis, '')) LIKE '%csat%' THEN 1 ELSE 0 END) AS kpi_csat_events,
                       SUM(CASE WHEN event_type = 'kpi_frt_recorded' THEN 1 ELSE 0 END) AS kpi_frt_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_ttr_recorded' THEN 1 ELSE 0 END) AS kpi_ttr_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_sla_breach_recorded' THEN 1 ELSE 0 END) AS kpi_sla_breach_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_dialogs_per_shift_recorded' THEN 1 ELSE 0 END) AS kpi_dialogs_per_shift_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_csat_recorded' THEN 1 ELSE 0 END) AS kpi_csat_recorded_events,
                       AVG(CASE WHEN event_type = 'kpi_frt_recorded' THEN duration_ms END) AS avg_frt_ms,
                       AVG(CASE WHEN event_type = 'kpi_ttr_recorded' THEN duration_ms END) AS avg_ttr_ms,
                       AVG(CASE WHEN event_type = 'workspace_open_ms' THEN duration_ms END) AS avg_open_ms
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND (? IS NULL OR experiment_name = ?)
                 GROUP BY COALESCE(experiment_cohort, 'unknown'), COALESCE(operator_segment, 'unknown')
                 ORDER BY events DESC, experiment_cohort ASC, operator_segment ASC
                """;
        try {
            Timestamp cutoffStart = Timestamp.from(windowStart);
            Timestamp cutoffEnd = Timestamp.from(windowEnd);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("experiment_cohort", rs.getString("experiment_cohort"));
                item.put("operator_segment", rs.getString("operator_segment"));
                item.put("events", rs.getLong("events"));
                item.put("render_errors", rs.getLong("render_errors"));
                item.put("fallbacks", rs.getLong("fallbacks"));
                item.put("abandons", rs.getLong("abandons"));
                item.put("workspace_open_events", rs.getLong("workspace_open_events"));
                item.put("context_profile_gap_events", rs.getLong("context_profile_gap_events"));
                item.put("context_source_gap_events", rs.getLong("context_source_gap_events"));
                item.put("context_attribute_policy_gap_events", rs.getLong("context_attribute_policy_gap_events"));
                item.put("context_block_gap_events", rs.getLong("context_block_gap_events"));
                item.put("context_contract_gap_events", rs.getLong("context_contract_gap_events"));
                item.put("context_sources_expanded_events", rs.getLong("context_sources_expanded_events"));
                item.put("context_attribute_policy_expanded_events", rs.getLong("context_attribute_policy_expanded_events"));
                item.put("context_extra_attributes_expanded_events", rs.getLong("context_extra_attributes_expanded_events"));
                item.put("workspace_sla_policy_gap_events", rs.getLong("workspace_sla_policy_gap_events"));
                item.put("workspace_parity_gap_events", rs.getLong("workspace_parity_gap_events"));
                item.put("workspace_inline_navigation_events", rs.getLong("workspace_inline_navigation_events"));
                item.put("manual_legacy_open_events", rs.getLong("manual_legacy_open_events"));
                item.put("workspace_open_legacy_blocked_events", rs.getLong("workspace_open_legacy_blocked_events"));
                item.put("workspace_rollout_packet_viewed_events", rs.getLong("workspace_rollout_packet_viewed_events"));
                item.put("workspace_rollout_review_confirmed_events", rs.getLong("workspace_rollout_review_confirmed_events"));
                item.put("workspace_rollout_review_decision_go_events", rs.getLong("workspace_rollout_review_decision_go_events"));
                item.put("workspace_rollout_review_decision_hold_events", rs.getLong("workspace_rollout_review_decision_hold_events"));
                item.put("workspace_rollout_review_decision_rollback_events", rs.getLong("workspace_rollout_review_decision_rollback_events"));
                item.put("workspace_rollout_review_incident_followup_linked_events", rs.getLong("workspace_rollout_review_incident_followup_linked_events"));
                item.put("workspace_sla_policy_review_updated_events", rs.getLong("workspace_sla_policy_review_updated_events"));
                item.put("workspace_macro_governance_review_updated_events", rs.getLong("workspace_macro_governance_review_updated_events"));
                item.put("workspace_macro_external_catalog_policy_updated_events", rs.getLong("workspace_macro_external_catalog_policy_updated_events"));
                item.put("workspace_macro_deprecation_policy_updated_events", rs.getLong("workspace_macro_deprecation_policy_updated_events"));
                item.put("workspace_legacy_usage_policy_updated_events", rs.getLong("workspace_legacy_usage_policy_updated_events"));
                item.put("slow_open_events", rs.getLong("slow_open_events"));
                item.put("kpi_frt_events", rs.getLong("kpi_frt_events"));
                item.put("kpi_ttr_events", rs.getLong("kpi_ttr_events"));
                item.put("kpi_sla_breach_events", rs.getLong("kpi_sla_breach_events"));
                item.put("kpi_dialogs_per_shift_events", rs.getLong("kpi_dialogs_per_shift_events"));
                item.put("kpi_csat_events", rs.getLong("kpi_csat_events"));
                item.put("kpi_frt_recorded_events", rs.getLong("kpi_frt_recorded_events"));
                item.put("kpi_ttr_recorded_events", rs.getLong("kpi_ttr_recorded_events"));
                item.put("kpi_sla_breach_recorded_events", rs.getLong("kpi_sla_breach_recorded_events"));
                item.put("kpi_dialogs_per_shift_recorded_events", rs.getLong("kpi_dialogs_per_shift_recorded_events"));
                item.put("kpi_csat_recorded_events", rs.getLong("kpi_csat_recorded_events"));
                item.put("avg_frt_ms", rs.getObject("avg_frt_ms") != null ? Math.round(rs.getDouble("avg_frt_ms")) : null);
                item.put("avg_ttr_ms", rs.getObject("avg_ttr_ms") != null ? Math.round(rs.getDouble("avg_ttr_ms")) : null);
                item.put("avg_open_ms", rs.getObject("avg_open_ms") != null ? Math.round(rs.getDouble("avg_open_ms")) : null);
                return item;
            }, cutoffStart, cutoffEnd, filterExperiment, filterExperiment);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace telemetry summary: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public List<Map<String, Object>> loadWorkspaceEventReasonBreakdown(String eventType,
                                                                       Instant windowStart,
                                                                       Instant windowEnd,
                                                                       String experimentName,
                                                                       int limit) {
        if (!StringUtils.hasText(eventType)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String filterExperiment = trimOrNull(experimentName);
        String sql = """
                SELECT LOWER(TRIM(COALESCE(reason, ''))) AS reason,
                       COUNT(*) AS events
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = ?
                   AND (? IS NULL OR experiment_name = ?)
                 GROUP BY LOWER(TRIM(COALESCE(reason, '')))
                 ORDER BY events DESC, reason ASC
                 LIMIT ?
                """;
        try {
            Timestamp cutoffStart = Timestamp.from(windowStart);
            Timestamp cutoffEnd = Timestamp.from(windowEnd);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String reason = normalizeNullString(rs.getString("reason"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", StringUtils.hasText(reason) ? reason : "unspecified");
                row.put("events", rs.getLong("events"));
                return row;
            }, cutoffStart, cutoffEnd, eventType.trim(), filterExperiment, filterExperiment, safeLimit);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace reason breakdown for {}: {}", eventType, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public Map<String, Object> loadWorkspaceGapBreakdown(Instant windowStart, Instant windowEnd, String experimentName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_profile_gap"));
        payload.put("source", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_source_gap"));
        payload.put("attribute_policy", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_attribute_policy_gap"));
        payload.put("block", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_block_gap"));
        payload.put("contract", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_contract_gap"));
        payload.put("sla_policy", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_sla_policy_gap"));
        payload.put("parity", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_parity_gap"));
        return payload;
    }

    public List<Map<String, Object>> aggregateWorkspaceGapReasons(List<Map<String, Object>> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        Map<String, GapBreakdownAccumulator> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            List<String> reasons = normalizeWorkspaceGapReasons(row.get("reason"));
            String ticketId = normalizeNullString(String.valueOf(row.getOrDefault("ticket_id", "")));
            Instant createdAt = row.get("created_at") instanceof Instant value ? value : null;
            for (String reason : reasons) {
                aggregates.computeIfAbsent(reason, GapBreakdownAccumulator::new)
                        .record(ticketId, createdAt);
            }
        }
        return aggregates.values().stream()
                .sorted(Comparator.comparingLong(GapBreakdownAccumulator::events).reversed()
                        .thenComparing(GapBreakdownAccumulator::reason))
                .limit(10)
                .map(GapBreakdownAccumulator::toMap)
                .toList();
    }

    public List<String> normalizeWorkspaceGapReasons(Object rawReason) {
        if (rawReason == null) {
            return List.of("unspecified");
        }
        String normalized = String.valueOf(rawReason).trim();
        if (normalized.isBlank()) {
            return List.of("unspecified");
        }
        List<String> reasons = Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10)
                .toList();
        return reasons.isEmpty() ? List.of("unspecified") : reasons;
    }

    private Map<String, Object> createTelemetryBucket(String dimension, String key) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put(dimension, key);
        bucket.put("events", 0L);
        bucket.put("render_errors", 0L);
        bucket.put("fallbacks", 0L);
        bucket.put("abandons", 0L);
        bucket.put("slow_open_events", 0L);
        bucket.put("avg_open_ms", null);
        bucket.put("_open_weight", 0L);
        bucket.put("_open_sum", 0L);
        return bucket;
    }

    private void mergeTelemetryMetrics(Map<String, Object> bucket, Map<String, Object> row) {
        if (bucket == null || row == null) {
            return;
        }

        long events = toLong(row.get("events"));
        long renderErrors = toLong(row.get("render_errors"));
        long fallbacks = toLong(row.get("fallbacks"));
        long abandons = toLong(row.get("abandons"));
        long slowOpenEvents = toLong(row.get("slow_open_events"));

        bucket.put("events", toLong(bucket.get("events")) + events);
        bucket.put("render_errors", toLong(bucket.get("render_errors")) + renderErrors);
        bucket.put("fallbacks", toLong(bucket.get("fallbacks")) + fallbacks);
        bucket.put("abandons", toLong(bucket.get("abandons")) + abandons);
        bucket.put("slow_open_events", toLong(bucket.get("slow_open_events")) + slowOpenEvents);

        Long avgOpen = extractNullableLong(row.get("avg_open_ms"));
        if (avgOpen == null) {
            return;
        }

        long weight = Math.max(events - renderErrors - fallbacks - abandons, 1L);
        long nextWeight = toLong(bucket.get("_open_weight")) + weight;
        long nextSum = toLong(bucket.get("_open_sum")) + avgOpen * weight;
        bucket.put("_open_weight", nextWeight);
        bucket.put("_open_sum", nextSum);
        bucket.put("avg_open_ms", Math.round((double) nextSum / nextWeight));
    }

    private String resolveOperatorDimension(String operatorSegment, String dimension) {
        if (!StringUtils.hasText(operatorSegment)) {
            return "unknown";
        }
        String normalized = operatorSegment.trim().toLowerCase();
        if ("team".equals(dimension)) {
            String explicitTeam = extractSegmentValue(normalized, "team");
            if (StringUtils.hasText(explicitTeam)) {
                return explicitTeam;
            }
            int separator = normalized.indexOf('/');
            if (separator > 0) {
                return normalized.substring(0, separator).trim();
            }
            return normalized.contains("_shift") ? "support" : normalized;
        }

        String explicitShift = extractSegmentValue(normalized, "shift");
        if (StringUtils.hasText(explicitShift)) {
            return explicitShift;
        }
        if (normalized.contains("night")) {
            return "night";
        }
        if (normalized.contains("morning")) {
            return "morning";
        }
        if (normalized.contains("evening")) {
            return "evening";
        }
        if (normalized.contains("day")) {
            return "day";
        }
        return "unknown";
    }

    private String extractSegmentValue(String segment, String key) {
        if (!StringUtils.hasText(segment) || !StringUtils.hasText(key)) {
            return null;
        }
        String needle = key + "=";
        int start = segment.indexOf(needle);
        if (start < 0) {
            return null;
        }
        String tail = segment.substring(start + needle.length());
        int delimiter = tail.indexOf(';');
        String value = delimiter >= 0 ? tail.substring(0, delimiter) : tail;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long extractNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<Map<String, Object>> loadWorkspaceGapBreakdownRows(Instant windowStart,
                                                                    Instant windowEnd,
                                                                    String experimentName,
                                                                    String eventType) {
        String filterExperiment = StringUtils.hasText(experimentName) ? experimentName.trim() : null;
        String sql = """
                SELECT reason, ticket_id, created_at
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = ?
                   AND (? IS NULL OR experiment_name = ?)
                 ORDER BY created_at DESC
                """;
        try {
            List<Map<String, Object>> rawRows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reason", rs.getString("reason"));
                item.put("ticket_id", rs.getString("ticket_id"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                item.put("created_at", createdAt != null ? createdAt.toInstant() : null);
                return item;
            }, Timestamp.from(windowStart), Timestamp.from(windowEnd), eventType, filterExperiment, filterExperiment);
            return aggregateWorkspaceGapReasons(rawRows);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace gap breakdown for {}: {}", eventType, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    private String normalizeNullString(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static final class GapBreakdownAccumulator {

        private final String reason;
        private long events = 0L;
        private final Set<String> ticketIds = new LinkedHashSet<>();
        private Instant lastSeenAt;

        private GapBreakdownAccumulator(String reason) {
            this.reason = reason;
        }

        private void record(String ticketId, Instant createdAt) {
            events++;
            if (StringUtils.hasText(ticketId)) {
                ticketIds.add(ticketId.trim());
            }
            if (createdAt != null && (lastSeenAt == null || createdAt.isAfter(lastSeenAt))) {
                lastSeenAt = createdAt;
            }
        }

        private long events() {
            return events;
        }

        private String reason() {
            return reason;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", reason);
            payload.put("events", events);
            payload.put("tickets", ticketIds.size());
            payload.put("last_seen_at", lastSeenAt != null ? lastSeenAt.toString() : "");
            return payload;
        }
    }
}
