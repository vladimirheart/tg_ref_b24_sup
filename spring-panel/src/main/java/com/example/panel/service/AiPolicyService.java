package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiPolicyService {

    private static final Duration PATTERN_CACHE_TTL = Duration.ofMinutes(2);

    private final JdbcTemplate jdbcTemplate;

    private volatile List<SensitivePattern> cachedPatterns = List.of();
    private volatile Instant cachedPatternsLoadedAt = Instant.EPOCH;

    public AiPolicyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SensitiveTopicMatch detectSensitiveTopic(String message) {
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized)) {
            return SensitiveTopicMatch.none();
        }
        for (SensitivePattern pattern : loadSensitivePatterns()) {
            if (!pattern.enabled()) {
                continue;
            }
            if (normalized.contains(pattern.patternNormalized())) {
                return new SensitiveTopicMatch(
                        true,
                        pattern.topicKey(),
                        pattern.severity(),
                        pattern.action(),
                        pattern.patternRaw()
                );
            }
        }
        return SensitiveTopicMatch.none();
    }

    public String applySensitiveModeOverride(String currentMode, SensitiveTopicMatch match) {
        if (match == null || !match.matched()) {
            return normalizeMode(currentMode);
        }
        if ("assist_only".equalsIgnoreCase(match.action())) {
            return "assist_only";
        }
        return normalizeMode(currentMode);
    }

    public boolean requiresEscalation(SensitiveTopicMatch match) {
        return match != null
                && match.matched()
                && "escalate_only".equalsIgnoreCase(match.action());
    }

    public boolean isAutoReplyEligibleSource(String source,
                                             String status,
                                             String trustLevel,
                                             String sourceType,
                                             String safetyLevel) {
        String normalizedSource = normalize(source);
        String normalizedStatus = normalizeOrDefault(status, "approved");
        String normalizedTrust = normalizeOrDefault(trustLevel, "low");
        String normalizedSourceType = normalizeOrDefault(sourceType, normalizedSource);
        String normalizedSafety = normalizeOrDefault(safetyLevel, "normal");

        if ("knowledge".equals(normalizedSource)) {
            return !"high_risk".equals(normalizedSafety);
        }
        if (!"memory".equals(normalizedSource)) {
            return false;
        }
        if (!"approved".equals(normalizedStatus)) {
            return false;
        }
        if ("low".equals(normalizedTrust)) {
            return false;
        }
        if ("history".equals(normalizedSourceType) || "applicant_history".equals(normalizedSourceType)) {
            return false;
        }
        return !"high_risk".equals(normalizedSafety);
    }

    public String normalizeTrustLevel(String value, String fallback) {
        String normalized = normalizeOrDefault(value, normalizeOrDefault(fallback, "low"));
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }

    public String normalizeSourceType(String source, String sourceType) {
        String normalized = normalize(sourceType);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return normalizeOrDefault(source, "unknown");
    }

    public String normalizeStatus(String value, String fallback) {
        String normalized = normalizeOrDefault(value, normalizeOrDefault(fallback, "draft"));
        return switch (normalized) {
            case "approved", "draft", "deprecated", "rejected" -> normalized;
            default -> "draft";
        };
    }

    public String normalizeSafetyLevel(String value, String fallback) {
        String normalized = normalizeOrDefault(value, normalizeOrDefault(fallback, "normal"));
        return switch (normalized) {
            case "normal", "high_risk" -> normalized;
            default -> "normal";
        };
    }

    private String normalizeMode(String mode) {
        String normalized = normalizeOrDefault(mode, "auto_reply");
        return switch (normalized) {
            case "auto_reply", "assist_only", "escalate_only" -> normalized;
            default -> "auto_reply";
        };
    }

    private List<SensitivePattern> loadSensitivePatterns() {
        Instant now = Instant.now();
        List<SensitivePattern> cached = cachedPatterns;
        if (!cached.isEmpty() && Duration.between(cachedPatternsLoadedAt, now).compareTo(PATTERN_CACHE_TTL) < 0) {
            return cached;
        }
        synchronized (this) {
            now = Instant.now();
            if (!cachedPatterns.isEmpty() && Duration.between(cachedPatternsLoadedAt, now).compareTo(PATTERN_CACHE_TTL) < 0) {
                return cachedPatterns;
            }
            List<SensitivePattern> loaded = querySensitivePatterns();
            if (loaded.isEmpty()) {
                loaded = defaultPatterns();
            }
            cachedPatterns = loaded;
            cachedPatternsLoadedAt = now;
            return loaded;
        }
    }

    private List<SensitivePattern> querySensitivePatterns() {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT pattern, topic_key, severity, action, enabled
                      FROM ai_agent_sensitive_patterns
                     WHERE enabled = 1
                     ORDER BY id ASC
                    """,
                    (rs, rowNum) -> {
                        String patternRaw = trim(rs.getString("pattern"));
                        if (!StringUtils.hasText(patternRaw)) {
                            return null;
                        }
                        String patternNormalized = normalize(patternRaw);
                        if (!StringUtils.hasText(patternNormalized)) {
                            return null;
                        }
                        return new SensitivePattern(
                                patternRaw,
                                patternNormalized,
                                normalizeOrDefault(rs.getString("topic_key"), "general_risk"),
                                normalizeOrDefault(rs.getString("severity"), "medium"),
                                normalizeOrDefault(rs.getString("action"), "assist_only"),
                                rs.getInt("enabled") > 0
                        );
                    }
            ).stream().filter(item -> item != null).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SensitivePattern> defaultPatterns() {
        List<Map<String, String>> defaults = List.of(
                Map.of("pattern", "возврат", "topic", "refund_money", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "refund", "topic", "refund_money", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "списали", "topic", "payment_issue", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "карта", "topic", "payment_issue", "severity", "high", "action", "assist_only"),
                Map.of("pattern", "паспорт", "topic", "personal_data", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "персональные данные", "topic", "personal_data", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "суд", "topic", "legal_complaint", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "претензия", "topic", "legal_complaint", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "отрав", "topic", "food_safety", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "аллерг", "topic", "food_safety", "severity", "high", "action", "escalate_only"),
                Map.of("pattern", "доставка опоздала", "topic", "delivery_dispute", "severity", "medium", "action", "assist_only"),
                Map.of("pattern", "вип", "topic", "vip_high_risk", "severity", "high", "action", "escalate_only")
        );
        List<SensitivePattern> items = new ArrayList<>();
        for (Map<String, String> row : defaults) {
            String patternRaw = row.get("pattern");
            String patternNormalized = normalize(patternRaw);
            if (!StringUtils.hasText(patternNormalized)) {
                continue;
            }
            items.add(new SensitivePattern(
                    patternRaw,
                    patternNormalized,
                    normalizeOrDefault(row.get("topic"), "general_risk"),
                    normalizeOrDefault(row.get("severity"), "medium"),
                    normalizeOrDefault(row.get("action"), "assist_only"),
                    true
            ));
        }
        return items;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return normalize(fallback);
        }
        return normalized;
    }

    public record SensitiveTopicMatch(boolean matched,
                                      String topicKey,
                                      String severity,
                                      String action,
                                      String pattern) {
        public static SensitiveTopicMatch none() {
            return new SensitiveTopicMatch(false, null, null, null, null);
        }

        public Map<String, Object> asPayload() {
            if (!matched) {
                return Map.of("sensitive_topic", 0);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sensitive_topic", 1);
            payload.put("sensitive_topic_key", topicKey);
            payload.put("sensitive_topic_severity", severity);
            payload.put("sensitive_topic_action", action);
            payload.put("sensitive_pattern", pattern);
            return payload;
        }
    }

    private record SensitivePattern(String patternRaw,
                                    String patternNormalized,
                                    String topicKey,
                                    String severity,
                                    String action,
                                    boolean enabled) {
    }
}

