package com.example.panel.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsMacroGovernancePolicyService {

    private final SharedConfigService sharedConfigService;
    private final DialogAuditService dialogAuditService;

    public AnalyticsMacroGovernancePolicyService(SharedConfigService sharedConfigService,
                                                 DialogAuditService dialogAuditService) {
        this.sharedConfigService = sharedConfigService;
        this.dialogAuditService = dialogAuditService;
    }

    public ResponseEntity<?> updateMacroGovernanceReview(Authentication authentication,
                                                         String reviewedByRaw,
                                                         String reviewedAtRaw,
                                                         String reviewNoteRaw,
                                                         String cleanupTicketIdRaw,
                                                         String decisionRaw) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(reviewedByRaw);
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
        OffsetDateTime reviewedAtUtc = resolveUtcTimestamp(reviewedAtRaw, "reviewed_at_utc");
        if (reviewedAtUtc == null && normalize(reviewedAtRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
            ));
        }
        if (reviewedAtUtc == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        }

        String reviewNote = trimLength(normalize(reviewNoteRaw), 500);
        String cleanupTicketId = trimLength(normalize(cleanupTicketIdRaw), 80);
        String decision = normalizeDecision(decisionRaw);
        if (decision == null && normalize(decisionRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "decision must be one of: go, hold"
            ));
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = mutableDialogConfig(settings);
        dialogConfig.put("macro_governance_reviewed_by", reviewedBy);
        dialogConfig.put("macro_governance_reviewed_at", reviewedAtUtc.toInstant().toString());
        putOrRemove(dialogConfig, "macro_governance_review_note", reviewNote);
        putOrRemove(dialogConfig, "macro_governance_cleanup_ticket_id", cleanupTicketId);
        putOrRemove(dialogConfig, "macro_governance_review_decision", decision);
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        logWorkspaceTelemetry(actor, "workspace_macro_governance_review_updated", "analytics_macro_governance_review");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "review_note", reviewNote == null ? "" : reviewNote,
                "cleanup_ticket_id", cleanupTicketId == null ? "" : cleanupTicketId,
                "decision", decision == null ? "" : decision
        ));
    }

    public ResponseEntity<?> updateMacroExternalCatalogPolicy(Authentication authentication,
                                                              String verifiedByRaw,
                                                              String verifiedAtRaw,
                                                              String expectedVersionRaw,
                                                              String observedVersionRaw,
                                                              String reviewNoteRaw,
                                                              String decisionRaw,
                                                              Object reviewTtlHoursRaw) {
        String actor = authentication != null ? authentication.getName() : null;
        String verifiedBy = normalize(verifiedByRaw);
        if (verifiedBy == null) {
            verifiedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, verifiedBy);
        OffsetDateTime verifiedAtUtc = resolveUtcTimestamp(verifiedAtRaw, "verified_at_utc");
        if (verifiedAtUtc == null && normalize(verifiedAtRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "verified_at_utc must be a valid UTC timestamp (ISO-8601)"
            ));
        }
        if (verifiedAtUtc == null) {
            verifiedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        }

        String expectedVersion = trimLength(normalize(expectedVersionRaw), 120);
        String observedVersion = trimLength(normalize(observedVersionRaw), 120);
        String reviewNote = trimLength(normalize(reviewNoteRaw), 500);
        String decision = normalizeDecision(decisionRaw);
        if (decision == null && normalize(decisionRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "decision must be one of: go, hold"
            ));
        }
        long reviewTtlHours = normalizeReviewTtlHours(reviewTtlHoursRaw);

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = mutableDialogConfig(settings);
        dialogConfig.put("macro_external_catalog_verified_by", verifiedBy);
        dialogConfig.put("macro_external_catalog_verified_at", verifiedAtUtc.toInstant().toString());
        dialogConfig.put("macro_external_catalog_contract_ttl_hours", reviewTtlHours);
        putOrRemove(dialogConfig, "macro_external_catalog_expected_version", expectedVersion);
        putOrRemove(dialogConfig, "macro_external_catalog_observed_version", observedVersion);
        putOrRemove(dialogConfig, "macro_external_catalog_review_note", reviewNote);
        putOrRemove(dialogConfig, "macro_external_catalog_decision", decision);
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        logWorkspaceTelemetry(actor, "workspace_macro_external_catalog_policy_updated", "analytics_macro_external_catalog_policy");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "verified_by", verifiedBy,
                "verified_at_utc", verifiedAtUtc.toInstant().toString(),
                "expected_version", expectedVersion == null ? "" : expectedVersion,
                "observed_version", observedVersion == null ? "" : observedVersion,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision", decision == null ? "" : decision,
                "review_ttl_hours", reviewTtlHours
        ));
    }

    public ResponseEntity<?> updateMacroDeprecationPolicy(Authentication authentication,
                                                          String reviewedByRaw,
                                                          String reviewedAtRaw,
                                                          String deprecationTicketIdRaw,
                                                          String reviewNoteRaw,
                                                          String decisionRaw,
                                                          Object reviewTtlHoursRaw) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(reviewedByRaw);
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
        OffsetDateTime reviewedAtUtc = resolveUtcTimestamp(reviewedAtRaw, "reviewed_at_utc");
        if (reviewedAtUtc == null && normalize(reviewedAtRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
            ));
        }
        if (reviewedAtUtc == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        }

        String deprecationTicketId = trimLength(normalize(deprecationTicketIdRaw), 80);
        String reviewNote = trimLength(normalize(reviewNoteRaw), 500);
        String decision = normalizeDecision(decisionRaw);
        if (decision == null && normalize(decisionRaw) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "decision must be one of: go, hold"
            ));
        }
        long reviewTtlHours = normalizeReviewTtlHours(reviewTtlHoursRaw);

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = mutableDialogConfig(settings);
        dialogConfig.put("macro_deprecation_policy_reviewed_by", reviewedBy);
        dialogConfig.put("macro_deprecation_policy_reviewed_at", reviewedAtUtc.toInstant().toString());
        dialogConfig.put("macro_deprecation_policy_ttl_hours", reviewTtlHours);
        putOrRemove(dialogConfig, "macro_deprecation_policy_ticket_id", deprecationTicketId);
        putOrRemove(dialogConfig, "macro_deprecation_policy_review_note", reviewNote);
        putOrRemove(dialogConfig, "macro_deprecation_policy_decision", decision);
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        logWorkspaceTelemetry(actor, "workspace_macro_deprecation_policy_updated", "analytics_macro_deprecation_policy");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "deprecation_ticket_id", deprecationTicketId == null ? "" : deprecationTicketId,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision", decision == null ? "" : decision,
                "review_ttl_hours", reviewTtlHours
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableDialogConfig(Map<String, Object> settings) {
        return settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private void putOrRemove(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            target.remove(key);
        } else {
            target.put(key, value);
        }
    }

    private void logWorkspaceTelemetry(String actor, String action, String source) {
        dialogAuditService.logWorkspaceTelemetry(
                actor,
                action,
                "experiment",
                null,
                source,
                null,
                "workspace.v1",
                null,
                "workspace_v1_rollout",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String normalizeDecision(String raw) {
        String decision = normalize(raw);
        if (decision == null) {
            return null;
        }
        decision = decision.toLowerCase(Locale.ROOT);
        if (!"go".equals(decision) && !"hold".equals(decision)) {
            return null;
        }
        return decision;
    }

    private long normalizeReviewTtlHours(Object raw) {
        long value = parsePositiveLong(raw);
        if (value <= 0) {
            value = 168;
        }
        return Math.min(value, 24L * 90L);
    }

    private String trimLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private OffsetDateTime resolveUtcTimestamp(String raw, String fieldName) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        return parseUtcTimestamp(normalized);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static String resolveActor(Authentication authentication, String fallback) {
        if (authentication != null && normalize(authentication.getName()) != null) {
            return authentication.getName();
        }
        if (normalize(fallback) != null) {
            return fallback;
        }
        return "anonymous";
    }

    private static OffsetDateTime parseUtcTimestamp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value);
            if (!ZoneOffset.UTC.equals(parsed.getOffset())) {
                return null;
            }
            return parsed.withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                Instant parsed = Instant.parse(value);
                return value.endsWith("Z") ? parsed.atOffset(ZoneOffset.UTC) : null;
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static long parsePositiveLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
