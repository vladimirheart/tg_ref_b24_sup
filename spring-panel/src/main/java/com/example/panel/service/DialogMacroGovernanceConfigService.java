package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DialogMacroGovernanceConfigService {

    private static final int DEFAULT_MACRO_GOVERNANCE_UNUSED_DAYS = 30;
    private static final long DEFAULT_MACRO_GOVERNANCE_REVIEW_TTL_HOURS = 24L * 90L;
    private static final long DEFAULT_MACRO_GOVERNANCE_CHECKPOINT_TTL_HOURS = 24L * 7L;

    private final DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService;

    public DialogMacroGovernanceConfigService(DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService) {
        this.dialogMacroGovernanceSupportService = dialogMacroGovernanceSupportService;
    }

    public AuditConfig resolve(Map<String, Object> settings) {
        Map<String, Object> safeSettings = settings == null ? Map.of() : settings;
        Object rawDialogConfig = safeSettings.get("dialog_config");
        Map<String, Object> dialogConfig = rawDialogConfig instanceof Map<?, ?> map ? castObjectMap(map) : Map.of();
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> templates = safeListOfMaps(dialogConfig.get("macro_templates"));
        boolean requireOwner = resolveBooleanConfig(dialogConfig, "macro_governance_require_owner", false);
        boolean requireNamespace = resolveBooleanConfig(dialogConfig, "macro_governance_require_namespace", false);
        boolean requireReview = resolveBooleanConfig(dialogConfig, "macro_governance_require_review", false);
        boolean deprecationRequiresReason = resolveBooleanConfig(dialogConfig, "macro_governance_deprecation_requires_reason", false);
        boolean redListEnabled = resolveBooleanConfig(dialogConfig, "macro_governance_red_list_enabled", false);
        boolean ownerActionRequired = resolveBooleanConfig(dialogConfig, "macro_governance_owner_action_required", false);
        boolean aliasCleanupRequired = resolveBooleanConfig(dialogConfig, "macro_governance_alias_cleanup_required", false);
        boolean variableCleanupRequired = resolveBooleanConfig(dialogConfig, "macro_governance_variable_cleanup_required", false);
        boolean usageTierSlaRequired = resolveBooleanConfig(dialogConfig, "macro_governance_usage_tier_sla_required", false);
        long reviewTtlHours = resolveLongConfig(dialogConfig, "macro_governance_review_ttl_hours", DEFAULT_MACRO_GOVERNANCE_REVIEW_TTL_HOURS, 1, 24L * 365L);
        int usageWindowDays = (int) resolveLongConfig(dialogConfig, "macro_governance_unused_days", DEFAULT_MACRO_GOVERNANCE_UNUSED_DAYS, 1, 365);
        int redListUsageMax = (int) resolveLongConfig(dialogConfig, "macro_governance_red_list_usage_max", 0, 0, 10000);
        int cleanupCadenceDays = (int) resolveLongConfig(dialogConfig, "macro_governance_cleanup_cadence_days", 0, 0, 365);
        int usageTierLowMax = (int) resolveLongConfig(dialogConfig, "macro_governance_usage_tier_low_max", 0, 0, 10000);
        int usageTierMediumMax = (int) resolveLongConfig(dialogConfig, "macro_governance_usage_tier_medium_max", 5, 0, 10000);
        if (usageTierMediumMax < usageTierLowMax) {
            usageTierMediumMax = usageTierLowMax;
        }
        int cleanupSlaLowDays = (int) resolveLongConfig(dialogConfig, "macro_governance_cleanup_sla_low_days", 7, 1, 365);
        int cleanupSlaMediumDays = (int) resolveLongConfig(dialogConfig, "macro_governance_cleanup_sla_medium_days", 30, 1, 365);
        int cleanupSlaHighDays = (int) resolveLongConfig(dialogConfig, "macro_governance_cleanup_sla_high_days", 90, 1, 365);
        int deprecationSlaLowDays = (int) resolveLongConfig(dialogConfig, "macro_governance_deprecation_sla_low_days", 14, 1, 365);
        int deprecationSlaMediumDays = (int) resolveLongConfig(dialogConfig, "macro_governance_deprecation_sla_medium_days", 45, 1, 365);
        int deprecationSlaHighDays = (int) resolveLongConfig(dialogConfig, "macro_governance_deprecation_sla_high_days", 120, 1, 365);
        Set<String> knownMacroVariables = dialogMacroGovernanceSupportService.resolveKnownMacroVariableKeys(dialogConfig);
        return new AuditConfig(
                dialogConfig,
                generatedAt,
                templates,
                requireOwner,
                requireNamespace,
                requireReview,
                deprecationRequiresReason,
                redListEnabled,
                ownerActionRequired,
                aliasCleanupRequired,
                variableCleanupRequired,
                usageTierSlaRequired,
                reviewTtlHours,
                usageWindowDays,
                redListUsageMax,
                cleanupCadenceDays,
                usageTierLowMax,
                usageTierMediumMax,
                cleanupSlaLowDays,
                cleanupSlaMediumDays,
                cleanupSlaHighDays,
                deprecationSlaLowDays,
                deprecationSlaMediumDays,
                deprecationSlaHighDays,
                knownMacroVariables
        );
    }

    long resolveLongConfig(Map<String, Object> source, String key, long fallback, long minInclusive, long maxInclusive) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw == null) {
            return fallback;
        }
        long parsed;
        if (raw instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(raw).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return parsed < minInclusive || parsed > maxInclusive ? fallback : parsed;
    }

    boolean resolveBooleanConfig(Map<String, Object> source, String key, boolean fallback) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        return raw == null ? fallback : toBoolean(raw);
    }

    List<Map<String, Object>> safeListOfMaps(Object value) {
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

    Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    String normalizeNullString(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.trim();
    }

    OffsetDateTime parseReviewTimestamp(String rawValue) {
        String value = normalizeNullString(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return java.sql.Timestamp.valueOf(value).toInstant().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    String normalizeUtcTimestamp(Object rawValue) {
        OffsetDateTime parsed = parseReviewTimestamp(rawValue == null ? null : String.valueOf(rawValue));
        return parsed != null ? parsed.toString() : "";
    }

    String normalizeDecision(String rawValue) {
        String decision = normalizeNullString(rawValue);
        if (!StringUtils.hasText(decision)) {
            return null;
        }
        String normalized = decision.toLowerCase(Locale.ROOT);
        return "go".equals(normalized) || "hold".equals(normalized) ? normalized : null;
    }

    public record AuditConfig(Map<String, Object> dialogConfig,
                              OffsetDateTime generatedAt,
                              List<Map<String, Object>> templates,
                              boolean requireOwner,
                              boolean requireNamespace,
                              boolean requireReview,
                              boolean deprecationRequiresReason,
                              boolean redListEnabled,
                              boolean ownerActionRequired,
                              boolean aliasCleanupRequired,
                              boolean variableCleanupRequired,
                              boolean usageTierSlaRequired,
                              long reviewTtlHours,
                              int usageWindowDays,
                              int redListUsageMax,
                              int cleanupCadenceDays,
                              int usageTierLowMax,
                              int usageTierMediumMax,
                              int cleanupSlaLowDays,
                              int cleanupSlaMediumDays,
                              int cleanupSlaHighDays,
                              int deprecationSlaLowDays,
                              int deprecationSlaMediumDays,
                              int deprecationSlaHighDays,
                              Set<String> knownMacroVariables) {
    }
}
