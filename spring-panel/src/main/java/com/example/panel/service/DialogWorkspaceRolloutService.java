package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogWorkspaceRolloutService {

    public Map<String, Object> resolveRolloutMeta(Map<String, Object> settings) {
        Object dialogConfigRaw = settings != null ? settings.get("dialog_config") : null;
        Map<?, ?> dialogConfig = dialogConfigRaw instanceof Map<?, ?> map ? map : Map.of();

        boolean workspaceEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_v1", true);
        boolean workspaceSingleMode = resolveBooleanDialogConfig(dialogConfig, "workspace_single_mode", false);
        boolean forceWorkspace = resolveBooleanDialogConfig(dialogConfig, "workspace_force_workspace", false) || workspaceSingleMode;
        boolean decommissionLegacyModal = resolveBooleanDialogConfig(dialogConfig, "workspace_decommission_legacy_modal", false) || workspaceSingleMode;
        boolean disableLegacyFallback = resolveBooleanDialogConfig(dialogConfig, "workspace_disable_legacy_fallback", false)
                || forceWorkspace
                || decommissionLegacyModal;
        boolean abEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_ab_enabled", false) && !workspaceSingleMode;
        int rolloutPercent = resolveIntegerDialogConfig(dialogConfig, "workspace_ab_rollout_percent", 0, 0, 100);
        String experimentName = trimToNull(String.valueOf(dialogConfig.get("workspace_ab_experiment_name")));
        String operatorSegment = trimToNull(String.valueOf(dialogConfig.get("workspace_ab_operator_segment")));
        OffsetDateTime reviewedAtUtc = parseUtcTimestamp(trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_external_kpi_reviewed_at"))));
        OffsetDateTime dataUpdatedAtUtc = parseUtcTimestamp(trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_external_kpi_data_updated_at"))));
        boolean legacyManualOpenPolicyEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_policy_enabled", false);
        boolean legacyManualOpenReasonRequired = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_reason_required", true);
        boolean legacyManualOpenBlockOnHold = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_block_on_hold", false);
        boolean legacyManualOpenBlockOnStaleReview = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_block_on_stale_review", false);
        int legacyManualOpenReviewTtlHours = resolveIntegerDialogConfig(dialogConfig,
                "workspace_rollout_legacy_manual_open_review_ttl_hours", 168, 1, 24 * 60);
        List<String> legacyManualAllowedReasons = safeStringList(dialogConfig.get("workspace_rollout_legacy_manual_open_allowed_reasons"));
        boolean legacyManualReasonCatalogRequired = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_reason_catalog_required", false);
        String legacyUsageDecision = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_decision")));
        if (legacyUsageDecision != null) {
            legacyUsageDecision = legacyUsageDecision.toLowerCase(Locale.ROOT);
        }
        String legacyUsageReviewedBy = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_by")));
        String legacyUsageReviewNote = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_review_note")));
        String legacyUsageReviewedAtRaw = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_at")));
        OffsetDateTime legacyUsageReviewedAtUtc = parseUtcTimestamp(legacyUsageReviewedAtRaw);
        boolean legacyUsageReviewInvalidUtc = StringUtils.hasText(legacyUsageReviewedAtRaw) && legacyUsageReviewedAtUtc == null;
        Long legacyUsageReviewAgeHours = null;
        if (legacyUsageReviewedAtUtc != null) {
            long hours = Duration.between(legacyUsageReviewedAtUtc.toInstant(), Instant.now()).toHours();
            legacyUsageReviewAgeHours = Math.max(0L, hours);
        }
        boolean legacyUsageReviewStale = legacyManualOpenPolicyEnabled
                && legacyManualOpenBlockOnStaleReview
                && (legacyUsageReviewedAtUtc == null || legacyUsageReviewAgeHours == null || legacyUsageReviewAgeHours > legacyManualOpenReviewTtlHours);
        boolean legacyUsageDecisionHold = legacyManualOpenPolicyEnabled
                && legacyManualOpenBlockOnHold
                && "hold".equalsIgnoreCase(legacyUsageDecision);
        boolean legacyManualOpenBlocked = legacyUsageDecisionHold || legacyUsageReviewStale || legacyUsageReviewInvalidUtc;
        String legacyManualOpenBlockReason = null;
        if (legacyManualOpenBlocked) {
            if (legacyUsageReviewInvalidUtc) {
                legacyManualOpenBlockReason = "invalid_review_timestamp";
            } else if (legacyUsageDecisionHold) {
                legacyManualOpenBlockReason = "review_decision_hold";
            } else if (legacyUsageReviewStale) {
                legacyManualOpenBlockReason = "stale_review";
            }
        }
        String mode;
        String bannerTone;
        if (!workspaceEnabled) {
            mode = "legacy_primary";
            bannerTone = "warning";
        } else if (workspaceSingleMode) {
            mode = "workspace_single_mode";
            bannerTone = "success";
        } else if (forceWorkspace || decommissionLegacyModal || !abEnabled) {
            mode = "workspace_primary";
            bannerTone = disableLegacyFallback ? "success" : "info";
        } else {
            mode = "cohort_rollout";
            bannerTone = disableLegacyFallback ? "warning" : "info";
        }

        String summary;
        if (!workspaceEnabled) {
            summary = "Workspace выключен: используется legacy modal.";
        } else if (workspaceSingleMode) {
            summary = "Workspace-only режим включён: legacy modal отключён, fallback недоступен, A/B rollout выключен.";
        } else if (disableLegacyFallback) {
            summary = "Workspace — основной режим. Auto-fallback в legacy отключён текущим rollout-режимом.";
        } else if (abEnabled) {
            summary = "Workspace включён в cohort-rollout; legacy modal остаётся fallback-механизмом.";
        } else {
            summary = "Workspace — основной режим. Legacy modal оставлен как rollback-механизм.";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspace_enabled", workspaceEnabled);
        payload.put("workspace_single_mode", workspaceSingleMode);
        payload.put("mode", mode);
        payload.put("banner_tone", bannerTone);
        payload.put("summary", summary);
        payload.put("force_workspace", forceWorkspace);
        payload.put("decommission_legacy_modal", decommissionLegacyModal);
        payload.put("legacy_fallback_available", workspaceEnabled && !disableLegacyFallback);
        payload.put("disable_legacy_fallback", disableLegacyFallback);
        payload.put("ab_enabled", abEnabled);
        payload.put("rollout_percent", rolloutPercent);
        payload.put("experiment_name", experimentName != null ? experimentName : "");
        payload.put("operator_segment", operatorSegment != null ? operatorSegment : "");
        Map<String, Object> legacyManualOpenPolicy = new LinkedHashMap<>();
        legacyManualOpenPolicy.put("enabled", legacyManualOpenPolicyEnabled);
        legacyManualOpenPolicy.put("reason_required", legacyManualOpenReasonRequired);
        legacyManualOpenPolicy.put("block_on_hold", legacyManualOpenBlockOnHold);
        legacyManualOpenPolicy.put("block_on_stale_review", legacyManualOpenBlockOnStaleReview);
        legacyManualOpenPolicy.put("review_ttl_hours", legacyManualOpenReviewTtlHours);
        legacyManualOpenPolicy.put("reviewed_by", legacyUsageReviewedBy != null ? legacyUsageReviewedBy : "");
        legacyManualOpenPolicy.put("review_note", legacyUsageReviewNote != null ? legacyUsageReviewNote : "");
        legacyManualOpenPolicy.put("review_timestamp_invalid", legacyUsageReviewInvalidUtc);
        legacyManualOpenPolicy.put("review_age_hours", legacyUsageReviewAgeHours == null ? "" : legacyUsageReviewAgeHours);
        legacyManualOpenPolicy.put("decision", legacyUsageDecision != null ? legacyUsageDecision : "");
        legacyManualOpenPolicy.put("allowed_reasons", legacyManualAllowedReasons);
        legacyManualOpenPolicy.put("reason_catalog_required", legacyManualReasonCatalogRequired);
        legacyManualOpenPolicy.put("blocked", legacyManualOpenBlocked);
        legacyManualOpenPolicy.put("block_reason", legacyManualOpenBlockReason != null ? legacyManualOpenBlockReason : "");
        if (legacyUsageReviewedAtUtc != null) {
            legacyManualOpenPolicy.put("reviewed_at_utc", legacyUsageReviewedAtUtc.toString());
        }
        payload.put("legacy_manual_open_policy", legacyManualOpenPolicy);
        if (reviewedAtUtc != null) {
            payload.put("reviewed_at_utc", reviewedAtUtc.toString());
        }
        if (dataUpdatedAtUtc != null) {
            payload.put("data_updated_at_utc", dataUpdatedAtUtc.toString());
        }
        return payload;
    }

    private boolean resolveBooleanDialogConfig(Map<?, ?> dialogConfig, String key, boolean fallbackValue) {
        if (dialogConfig == null || key == null) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return fallbackValue;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallbackValue;
        };
    }

    private int resolveIntegerDialogConfig(Map<?, ?> dialogConfig,
                                           String key,
                                           int fallbackValue,
                                           int minValue,
                                           int maxValue) {
        if (dialogConfig == null || key == null) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                return fallbackValue;
            }
        }
        if (parsed < minValue || parsed > maxValue) {
            return fallbackValue;
        }
        return parsed;
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fallback to legacy datetime-local without explicit offset
        }
        try {
            return LocalDateTime.parse(rawValue).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<String> safeStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(item -> trimToNull(String.valueOf(item)))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
