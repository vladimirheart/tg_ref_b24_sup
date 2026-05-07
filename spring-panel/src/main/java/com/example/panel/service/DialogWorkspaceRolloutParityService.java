package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DialogWorkspaceRolloutParityService {

    private static final Logger log = LoggerFactory.getLogger(DialogWorkspaceRolloutParityService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService;
    private final DialogWorkspaceRolloutGovernanceConfigService configService;

    public DialogWorkspaceRolloutParityService(JdbcTemplate jdbcTemplate,
                                               DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService,
                                               DialogWorkspaceRolloutGovernanceConfigService configService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogWorkspaceTelemetryDataService = dialogWorkspaceTelemetryDataService;
        this.configService = configService;
    }

    public DialogWorkspaceRolloutSectionResult buildParityExitCriteria(DialogWorkspaceRolloutGovernanceConfig config,
                                                                       String experimentName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean enabled = config.parityExitDays() > 0;
        List<String> normalizedCriticalReasons = config.parityCriticalReasons() == null ? List.of() : config.parityCriticalReasons().stream()
                .map(configService::normalizeNullString)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        payload.put("enabled", enabled);
        payload.put("window_days", config.parityExitDays());
        payload.put("critical_reasons", normalizedCriticalReasons);
        if (!enabled) {
            payload.put("ready", true);
            payload.put("critical_gap_events", 0L);
            payload.put("last_seen_at", "");
            payload.put("top_reasons", List.of());
            payload.put("critical_reasons_summary", "");
            payload.put("top_reasons_summary", "");
            return new DialogWorkspaceRolloutSectionResult(
                    "parity_exit_criteria",
                    "workspace",
                    "Parity exit criteria",
                    "off",
                    false,
                    "Legacy modal перестаёт считаться штатным UX только после окна без критичных parity-gap в UTC.",
                    "not required",
                    "optional",
                    "",
                    null,
                    payload
            );
        }

        String filterExperiment = StringUtils.hasText(experimentName) ? experimentName.trim() : null;
        String sql = """
                SELECT reason, ticket_id, created_at
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = 'workspace_parity_gap'
                   AND (? IS NULL OR experiment_name = ?)
                 ORDER BY created_at DESC
                """;
        Instant end = Instant.now();
        Instant start = end.minusSeconds(Math.max(1, config.parityExitDays()) * 24L * 60L * 60L);
        try {
            List<Map<String, Object>> rawRows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", configService.normalizeNullString(rs.getString("reason")));
                row.put("ticket_id", configService.normalizeNullString(rs.getString("ticket_id")));
                row.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : "");
                return row;
            }, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), filterExperiment, filterExperiment);
            List<Map<String, Object>> filteredRows = normalizedCriticalReasons.isEmpty()
                    ? rawRows
                    : rawRows.stream()
                    .filter(row -> normalizedCriticalReasons.contains(String.valueOf(row.getOrDefault("reason", "")).toLowerCase(Locale.ROOT)))
                    .toList();
            List<Map<String, Object>> topReasons = dialogWorkspaceTelemetryDataService.aggregateWorkspaceGapReasons(filteredRows);
            String lastSeenAt = filteredRows.stream()
                    .map(row -> configService.normalizeNullString(String.valueOf(row.getOrDefault("created_at", ""))))
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
            payload.put("ready", filteredRows.isEmpty());
            payload.put("critical_gap_events", (long) filteredRows.size());
            payload.put("last_seen_at", lastSeenAt);
            payload.put("top_reasons", topReasons);
            payload.put("critical_reasons_summary", String.join(", ", normalizedCriticalReasons));
            payload.put("top_reasons_summary", topReasons.stream()
                    .limit(3)
                    .map(row -> "%s(%d)".formatted(
                            String.valueOf(row.getOrDefault("reason", "unspecified")),
                            configService.toLong(row.get("events"))))
                    .collect(Collectors.joining(", ")));
            if (normalizedCriticalReasons.isEmpty()) {
                payload.put("error", "");
            } else if (filteredRows.isEmpty()) {
                payload.put("ready", false);
                payload.put("error", "telemetry_unavailable");
            } else {
                payload.put("error", "");
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to load parity exit criteria snapshot: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            payload.put("ready", false);
            payload.put("critical_gap_events", 0L);
            payload.put("last_seen_at", "");
            payload.put("top_reasons", List.of());
            payload.put("critical_reasons_summary", String.join(", ", normalizedCriticalReasons));
            payload.put("top_reasons_summary", "");
            payload.put("error", "telemetry_unavailable");
        }

        boolean ready = configService.toBoolean(payload.get("ready"));
        return new DialogWorkspaceRolloutSectionResult(
                "parity_exit_criteria",
                "workspace",
                "Parity exit criteria",
                ready ? "ok" : "hold",
                !ready,
                "Legacy modal перестаёт считаться штатным UX только после окна без критичных parity-gap в UTC.",
                "critical_gaps=%d".formatted(configService.toLong(payload.get("critical_gap_events"))),
                "0 critical gaps in last %d days UTC".formatted(configService.toLong(payload.get("window_days"))),
                String.valueOf(payload.getOrDefault("last_seen_at", "")),
                StringUtils.hasText(String.valueOf(payload.getOrDefault("top_reasons_summary", "")))
                        ? "top_reasons=" + payload.get("top_reasons_summary")
                        : StringUtils.hasText(String.valueOf(payload.getOrDefault("critical_reasons_summary", "")))
                        ? "critical=" + payload.get("critical_reasons_summary")
                        : null,
                payload
        );
    }
}
