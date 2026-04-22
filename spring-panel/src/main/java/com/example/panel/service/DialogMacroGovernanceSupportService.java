package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DialogMacroGovernanceSupportService {

    private static final Logger log = LoggerFactory.getLogger(DialogMacroGovernanceSupportService.class);
    private static final Pattern MACRO_VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-z0-9_]+)(?:\\s*\\|\\s*([^}]+))?\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> BUILTIN_MACRO_VARIABLE_KEYS = Set.of(
            "client_name",
            "ticket_id",
            "operator_name",
            "channel_name",
            "business",
            "location",
            "dialog_status",
            "created_at",
            "client_total_dialogs",
            "client_open_dialogs",
            "client_resolved_30d",
            "client_avg_rating",
            "client_segment_list",
            "current_date",
            "current_time"
    );

    private final JdbcTemplate jdbcTemplate;

    public DialogMacroGovernanceSupportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> loadMacroTemplateUsage(String templateId, String templateName, int usageWindowDays) {
        if (!StringUtils.hasText(templateId) && !StringUtils.hasText(templateName)) {
            return Map.of("usage_count", 0L, "preview_count", 0L, "error_count", 0L, "last_used_at", "");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT SUM(CASE WHEN event_type = 'macro_apply' THEN 1 ELSE 0 END) AS usage_count,
                       SUM(CASE WHEN event_type = 'macro_preview' THEN 1 ELSE 0 END) AS preview_count,
                       SUM(CASE WHEN error_code IS NOT NULL AND TRIM(CAST(error_code AS TEXT)) <> '' THEN 1 ELSE 0 END) AS error_count,
                       MAX(CASE WHEN event_type = 'macro_apply' THEN created_at ELSE NULL END) AS last_used_at
                  FROM workspace_telemetry_audit
                 WHERE event_type IN ('macro_apply', 'macro_preview')
                   AND created_at >= ?
                """);
        args.add(Timestamp.from(Instant.now().minusSeconds(Math.max(1, usageWindowDays) * 24L * 3600L)));
        if (StringUtils.hasText(templateId) && StringUtils.hasText(templateName)) {
            sql.append(" AND (template_id = ? OR template_name = ?)");
            args.add(templateId);
            args.add(templateName);
        } else if (StringUtils.hasText(templateId)) {
            sql.append(" AND template_id = ?");
            args.add(templateId);
        } else {
            sql.append(" AND template_name = ?");
            args.add(templateName);
        }
        try {
            return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("usage_count", rs.getLong("usage_count"));
                row.put("preview_count", rs.getLong("preview_count"));
                row.put("error_count", rs.getLong("error_count"));
                Object lastUsed = rs.getObject("last_used_at");
                row.put("last_used_at", lastUsed != null ? String.valueOf(lastUsed) : "");
                return row;
            }, args.toArray());
        } catch (DataAccessException ex) {
            log.warn("Unable to load macro usage audit for template {}: {}", templateId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Map.of("usage_count", 0L, "preview_count", 0L, "error_count", 0L, "last_used_at", "");
        }
    }

    public Set<String> resolveKnownMacroVariableKeys(Map<String, Object> dialogConfig) {
        Set<String> variables = new LinkedHashSet<>(BUILTIN_MACRO_VARIABLE_KEYS);
        safeListOfMaps(dialogConfig.get("macro_variable_catalog")).stream()
                .map(item -> normalizeNullString(String.valueOf(item.get("key"))))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(variables::add);
        Map<String, Object> defaults = dialogConfig.get("macro_variable_defaults") instanceof Map<?, ?> map
                ? castObjectMap(map)
                : Map.of();
        defaults.keySet().stream()
                .map(key -> normalizeNullString(String.valueOf(key)))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(variables::add);
        return variables;
    }

    public List<String> extractMacroTemplateVariables(String templateText) {
        if (!StringUtils.hasText(templateText)) {
            return List.of();
        }
        List<String> variables = new ArrayList<>();
        Matcher matcher = MACRO_VARIABLE_PATTERN.matcher(templateText);
        while (matcher.find()) {
            String key = normalizeNullString(matcher.group(1));
            if (StringUtils.hasText(key)) {
                String normalized = key.toLowerCase(Locale.ROOT);
                if (!variables.contains(normalized)) {
                    variables.add(normalized);
                }
            }
        }
        return variables;
    }

    public List<String> resolveMacroTagAliases(Object rawTags) {
        if (!(rawTags instanceof Collection<?> tags)) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> normalizeNullString(String.valueOf(tag)))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    public String resolveMacroUsageTier(long usageCount, int lowMax, int mediumMax) {
        if (usageCount <= lowMax) {
            return "low";
        }
        if (usageCount <= mediumMax) {
            return "medium";
        }
        return "high";
    }

    public int resolveMacroTierSlaDays(String usageTier, int lowDays, int mediumDays, int highDays) {
        return switch (String.valueOf(usageTier).toLowerCase(Locale.ROOT)) {
            case "low" -> lowDays;
            case "medium" -> mediumDays;
            default -> highDays;
        };
    }

    public Map<String, Object> buildMacroGovernanceIssue(String type,
                                                         String templateId,
                                                         String templateName,
                                                         String status,
                                                         String classification,
                                                         String summary,
                                                         String detail) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("type", type);
        issue.put("template_id", templateId);
        issue.put("template_name", templateName);
        issue.put("status", normalizeScorecardStatus(status));
        issue.put("classification", classification);
        issue.put("summary", summary);
        issue.put("detail", detail);
        return issue;
    }

    private List<Map<String, Object>> safeListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castObjectMap(map));
            }
        }
        return result;
    }

    private Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }

    private String normalizeNullString(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeScorecardStatus(String value) {
        String normalized = normalizeNullString(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "attention", "hold", "off" -> normalized;
            default -> "hold";
        };
    }
}
