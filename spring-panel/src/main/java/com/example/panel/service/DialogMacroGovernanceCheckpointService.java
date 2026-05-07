package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogMacroGovernanceCheckpointService {

    private final DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService;
    private final DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService;

    public DialogMacroGovernanceCheckpointService(DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService,
                                                  DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService) {
        this.dialogMacroGovernanceSupportService = dialogMacroGovernanceSupportService;
        this.dialogMacroGovernanceConfigService = dialogMacroGovernanceConfigService;
    }

    public CheckpointBundle evaluate(DialogMacroGovernanceConfigService.AuditConfig config, int missingOwnerTotal) {
        List<Map<String, Object>> issues = new ArrayList<>();

        CheckpointState governanceReview = buildGovernanceReviewCheckpoint(config, issues);
        CheckpointState externalCatalog = buildExternalCatalogCheckpoint(config, issues);
        CheckpointState deprecationPolicy = buildDeprecationPolicyCheckpoint(config, issues);

        List<String> minimumRequiredCheckpoints = new ArrayList<>();
        if (governanceReview.required()) {
            minimumRequiredCheckpoints.add("governance_review");
        }
        if (externalCatalog.required()) {
            minimumRequiredCheckpoints.add("external_catalog");
        }
        if (deprecationPolicy.required() && minimumRequiredCheckpoints.size() < 2) {
            minimumRequiredCheckpoints.add("deprecation_policy");
        }
        if (minimumRequiredCheckpoints.isEmpty() && config.requireOwner()) {
            minimumRequiredCheckpoints.add("template_owner");
        }

        List<String> advisorySignals = new ArrayList<>();
        if (config.redListEnabled()) {
            advisorySignals.add("red_list");
        }
        if (config.ownerActionRequired()) {
            advisorySignals.add("owner_action");
        }
        if (config.aliasCleanupRequired()) {
            advisorySignals.add("alias_cleanup");
        }
        if (config.variableCleanupRequired()) {
            advisorySignals.add("variable_cleanup");
        }
        if (config.usageTierSlaRequired()) {
            advisorySignals.add("usage_tier_sla");
        }

        Map<String, Boolean> requiredCheckpointState = new LinkedHashMap<>();
        requiredCheckpointState.put("governance_review", governanceReview.ready());
        requiredCheckpointState.put("external_catalog", externalCatalog.ready());
        requiredCheckpointState.put("deprecation_policy", deprecationPolicy.ready());
        requiredCheckpointState.put("template_owner", missingOwnerTotal == 0);

        long requiredCheckpointTotal = minimumRequiredCheckpoints.size();
        long requiredCheckpointReadyTotal = minimumRequiredCheckpoints.stream()
                .filter(key -> Boolean.TRUE.equals(requiredCheckpointState.get(key)))
                .count();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0
                ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal)
                : 100L;

        long freshnessCheckpointTotal = java.util.stream.Stream.of(
                governanceReview.required(),
                externalCatalog.required(),
                deprecationPolicy.required()
        ).filter(Boolean::booleanValue).count();
        if (config.requireOwner()) {
            freshnessCheckpointTotal += 1L;
        }
        long freshnessCheckpointReadyTotal = 0L;
        if (governanceReview.required() && governanceReview.reviewFresh()) {
            freshnessCheckpointReadyTotal += 1L;
        }
        if (externalCatalog.required() && externalCatalog.reviewFresh()) {
            freshnessCheckpointReadyTotal += 1L;
        }
        if (deprecationPolicy.required() && deprecationPolicy.reviewFresh()) {
            freshnessCheckpointReadyTotal += 1L;
        }
        if (config.requireOwner() && missingOwnerTotal == 0) {
            freshnessCheckpointReadyTotal += 1L;
        }
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0
                ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal)
                : 100L;
        boolean minimumRequiredPathControlled = requiredCheckpointClosureRatePct >= 100L && freshnessClosureRatePct >= 100L;

        return new CheckpointBundle(
                issues,
                governanceReview.payload(),
                externalCatalog.payload(),
                deprecationPolicy.payload(),
                minimumRequiredCheckpoints,
                advisorySignals.stream().distinct().toList(),
                requiredCheckpointTotal,
                requiredCheckpointReadyTotal,
                requiredCheckpointClosureRatePct,
                minimumRequiredPathControlled,
                freshnessCheckpointTotal,
                freshnessCheckpointReadyTotal,
                freshnessClosureRatePct
        );
    }

    private CheckpointState buildGovernanceReviewCheckpoint(DialogMacroGovernanceConfigService.AuditConfig config,
                                                            List<Map<String, Object>> issues) {
        Map<String, Object> dialogConfig = config.dialogConfig();
        boolean required = dialogMacroGovernanceConfigService.resolveBooleanConfig(dialogConfig, "macro_governance_review_required", config.requireReview());
        long reviewTtlHours = dialogMacroGovernanceConfigService.resolveLongConfig(dialogConfig, "macro_governance_checkpoint_ttl_hours", 24L * 7L, 1, 24L * 90L);
        boolean cleanupTicketRequired = dialogMacroGovernanceConfigService.resolveBooleanConfig(dialogConfig, "macro_governance_cleanup_ticket_required", false);
        String reviewedBy = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_by")));
        String reviewedAtRaw = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_at")));
        OffsetDateTime reviewedAt = dialogMacroGovernanceConfigService.parseReviewTimestamp(reviewedAtRaw);
        boolean reviewedAtInvalid = StringUtils.hasText(reviewedAtRaw) && reviewedAt == null;
        long reviewAgeHours = reviewedAt != null ? Math.max(0L, Duration.between(reviewedAt, config.generatedAt()).toHours()) : -1L;
        boolean reviewFresh = reviewedAt != null && reviewAgeHours <= reviewTtlHours && !reviewedAtInvalid;
        String reviewNote = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_review_note")));
        String decision = dialogMacroGovernanceConfigService.normalizeDecision(String.valueOf(dialogConfig.get("macro_governance_review_decision")));
        String cleanupTicketId = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_cleanup_ticket_id")));
        List<String> reviewIssues = new ArrayList<>();
        if (required) {
            if (!StringUtils.hasText(reviewedBy) || reviewedAt == null) {
                reviewIssues.add("governance_review_missing");
                issues.add(buildIssue("governance_review_missing", "hold", "rollout_blocker",
                        "Macro governance review checkpoint не заполнен.",
                        "reviewed_by/reviewed_at=missing"));
            } else if (reviewedAtInvalid) {
                reviewIssues.add("governance_review_invalid_utc");
                issues.add(buildIssue("governance_review_invalid_utc", "hold", "rollout_blocker",
                        "Дата macro governance review невалидна и не может быть интерпретирована как UTC.",
                        "reviewed_at=invalid"));
            } else if (!reviewFresh) {
                reviewIssues.add("governance_review_stale");
                issues.add(buildIssue("governance_review_stale", "hold", "rollout_blocker",
                        "Macro governance review устарел и требует повторной проверки.",
                        "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, reviewTtlHours)));
            }
            if (cleanupTicketRequired && !StringUtils.hasText(cleanupTicketId)) {
                reviewIssues.add("governance_cleanup_ticket_missing");
                issues.add(buildIssue("governance_cleanup_ticket_missing", "attention", "backlog_candidate",
                        "Для macro governance review требуется cleanup ticket.",
                        "cleanup_ticket_id=missing"));
            }
        }
        boolean ready = !required || (StringUtils.hasText(reviewedBy) && reviewFresh && (!cleanupTicketRequired || StringUtils.hasText(cleanupTicketId)));
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("required", required),
                Map.entry("ready", ready),
                Map.entry("reviewed_by", reviewedBy),
                Map.entry("reviewed_at_utc", reviewedAt == null ? "" : reviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", reviewedAtInvalid),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("review_age_hours", reviewAgeHours),
                Map.entry("cleanup_ticket_required", cleanupTicketRequired),
                Map.entry("cleanup_ticket_id", cleanupTicketId),
                Map.entry("decision", decision == null ? "" : decision),
                Map.entry("review_note", reviewNote),
                Map.entry("issues", reviewIssues)
        );
        return new CheckpointState(required, ready, reviewFresh, payload);
    }

    private CheckpointState buildExternalCatalogCheckpoint(DialogMacroGovernanceConfigService.AuditConfig config,
                                                           List<Map<String, Object>> issues) {
        Map<String, Object> dialogConfig = config.dialogConfig();
        boolean required = dialogMacroGovernanceConfigService.resolveBooleanConfig(dialogConfig, "macro_external_catalog_contract_required", config.requireReview());
        long reviewTtlHours = dialogMacroGovernanceConfigService.resolveLongConfig(dialogConfig, "macro_external_catalog_contract_ttl_hours", 168, 1, 24L * 90L);
        String expectedVersion = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_expected_version")));
        String observedVersion = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_observed_version")));
        String verifiedBy = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_by")));
        String verifiedAtRaw = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_at")));
        OffsetDateTime verifiedAt = dialogMacroGovernanceConfigService.parseReviewTimestamp(verifiedAtRaw);
        boolean verifiedAtInvalid = StringUtils.hasText(verifiedAtRaw) && verifiedAt == null;
        long reviewAgeHours = verifiedAt != null ? Math.max(0L, Duration.between(verifiedAt, config.generatedAt()).toHours()) : -1L;
        boolean reviewFresh = verifiedAt != null && reviewAgeHours <= reviewTtlHours && !verifiedAtInvalid;
        String reviewNote = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_review_note")));
        String decision = dialogMacroGovernanceConfigService.normalizeDecision(String.valueOf(dialogConfig.get("macro_external_catalog_decision")));
        List<String> checkpointIssues = new ArrayList<>();
        if (required) {
            if (!StringUtils.hasText(expectedVersion)) {
                checkpointIssues.add("external_catalog_expected_version_missing");
                issues.add(buildIssue("external_catalog_expected_version_missing", "hold", "rollout_blocker",
                        "Не задан expected version для external macro catalog.",
                        "expected_version=missing"));
            }
            if (!StringUtils.hasText(observedVersion)) {
                checkpointIssues.add("external_catalog_observed_version_missing");
                issues.add(buildIssue("external_catalog_observed_version_missing", "hold", "rollout_blocker",
                        "Не зафиксирована observed version для external macro catalog.",
                        "observed_version=missing"));
            }
            if (StringUtils.hasText(expectedVersion) && StringUtils.hasText(observedVersion)
                    && !expectedVersion.equalsIgnoreCase(observedVersion)) {
                checkpointIssues.add("external_catalog_version_mismatch");
                issues.add(buildIssue("external_catalog_version_mismatch", "hold", "rollout_blocker",
                        "Observed version external macro catalog не совпадает с ожидаемой.",
                        "expected=%s observed=%s".formatted(expectedVersion, observedVersion)));
            }
            if (!StringUtils.hasText(verifiedBy) || verifiedAt == null) {
                checkpointIssues.add("external_catalog_review_missing");
                issues.add(buildIssue("external_catalog_review_missing", "hold", "rollout_blocker",
                        "External catalog compatibility review не заполнен.",
                        "verified_by/verified_at=missing"));
            } else if (verifiedAtInvalid) {
                checkpointIssues.add("external_catalog_review_invalid_utc");
                issues.add(buildIssue("external_catalog_review_invalid_utc", "hold", "rollout_blocker",
                        "Дата compatibility review external macro catalog невалидна для UTC.",
                        "verified_at=invalid"));
            } else if (!reviewFresh) {
                checkpointIssues.add("external_catalog_review_stale");
                issues.add(buildIssue("external_catalog_review_stale", "hold", "rollout_blocker",
                        "External catalog compatibility review устарел.",
                        "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, reviewTtlHours)));
            }
        }
        boolean ready = !required || checkpointIssues.isEmpty();
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("required", required),
                Map.entry("ready", ready),
                Map.entry("expected_version", expectedVersion),
                Map.entry("observed_version", observedVersion),
                Map.entry("verified_by", verifiedBy),
                Map.entry("verified_at_utc", verifiedAt == null ? "" : verifiedAt.toString()),
                Map.entry("verified_at_invalid_utc", verifiedAtInvalid),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("review_age_hours", reviewAgeHours),
                Map.entry("decision", decision == null ? "" : decision),
                Map.entry("review_note", reviewNote),
                Map.entry("issues", checkpointIssues)
        );
        return new CheckpointState(required, ready, reviewFresh, payload);
    }

    private CheckpointState buildDeprecationPolicyCheckpoint(DialogMacroGovernanceConfigService.AuditConfig config,
                                                             List<Map<String, Object>> issues) {
        Map<String, Object> dialogConfig = config.dialogConfig();
        boolean required = dialogMacroGovernanceConfigService.resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_required", false);
        long reviewTtlHours = dialogMacroGovernanceConfigService.resolveLongConfig(dialogConfig, "macro_deprecation_policy_ttl_hours", 168, 1, 24L * 90L);
        boolean ticketRequired = dialogMacroGovernanceConfigService.resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_ticket_required", false);
        String reviewedBy = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_by")));
        String reviewedAtRaw = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_at")));
        OffsetDateTime reviewedAt = dialogMacroGovernanceConfigService.parseReviewTimestamp(reviewedAtRaw);
        boolean reviewedAtInvalid = StringUtils.hasText(reviewedAtRaw) && reviewedAt == null;
        long reviewAgeHours = reviewedAt != null ? Math.max(0L, Duration.between(reviewedAt, config.generatedAt()).toHours()) : -1L;
        boolean reviewFresh = reviewedAt != null && reviewAgeHours <= reviewTtlHours && !reviewedAtInvalid;
        String decision = dialogMacroGovernanceConfigService.normalizeDecision(String.valueOf(dialogConfig.get("macro_deprecation_policy_decision")));
        String reviewNote = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_review_note")));
        String ticketId = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_ticket_id")));
        List<String> checkpointIssues = new ArrayList<>();
        if (required) {
            if (!StringUtils.hasText(reviewedBy) || reviewedAt == null) {
                checkpointIssues.add("deprecation_policy_review_missing");
                issues.add(buildIssue("deprecation_policy_review_missing", "hold", "rollout_blocker",
                        "Macro deprecation policy review checkpoint не заполнен.",
                        "reviewed_by/reviewed_at=missing"));
            } else if (reviewedAtInvalid) {
                checkpointIssues.add("deprecation_policy_review_invalid_utc");
                issues.add(buildIssue("deprecation_policy_review_invalid_utc", "hold", "rollout_blocker",
                        "Дата macro deprecation policy review невалидна для UTC.",
                        "reviewed_at=invalid"));
            } else if (!reviewFresh) {
                checkpointIssues.add("deprecation_policy_review_stale");
                issues.add(buildIssue("deprecation_policy_review_stale", "hold", "rollout_blocker",
                        "Macro deprecation policy review устарел.",
                        "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, reviewTtlHours)));
            }
            if (ticketRequired && !StringUtils.hasText(ticketId)) {
                checkpointIssues.add("deprecation_policy_ticket_missing");
                issues.add(buildIssue("deprecation_policy_ticket_missing", "attention", "backlog_candidate",
                        "Для deprecation policy checkpoint требуется deprecation ticket.",
                        "deprecation_ticket_id=missing"));
            }
        }
        boolean ready = !required || checkpointIssues.isEmpty();
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("required", required),
                Map.entry("ready", ready),
                Map.entry("reviewed_by", reviewedBy),
                Map.entry("reviewed_at_utc", reviewedAt == null ? "" : reviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", reviewedAtInvalid),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("review_age_hours", reviewAgeHours),
                Map.entry("deprecation_ticket_required", ticketRequired),
                Map.entry("deprecation_ticket_id", ticketId),
                Map.entry("decision", decision == null ? "" : decision),
                Map.entry("review_note", reviewNote),
                Map.entry("issues", checkpointIssues)
        );
        return new CheckpointState(required, ready, reviewFresh, payload);
    }

    private Map<String, Object> buildIssue(String type,
                                           String status,
                                           String classification,
                                           String summary,
                                           String detail) {
        return dialogMacroGovernanceSupportService.buildMacroGovernanceIssue(
                type,
                null,
                null,
                status,
                classification,
                summary,
                detail
        );
    }

    private record CheckpointState(boolean required,
                                   boolean ready,
                                   boolean reviewFresh,
                                   Map<String, Object> payload) {
    }

    public record CheckpointBundle(List<Map<String, Object>> issues,
                                   Map<String, Object> governanceReview,
                                   Map<String, Object> externalCatalogContract,
                                   Map<String, Object> deprecationPolicy,
                                   List<String> minimumRequiredCheckpoints,
                                   List<String> advisorySignals,
                                   long requiredCheckpointTotal,
                                   long requiredCheckpointReadyTotal,
                                   long requiredCheckpointClosureRatePct,
                                   boolean minimumRequiredPathControlled,
                                   long freshnessCheckpointTotal,
                                   long freshnessCheckpointReadyTotal,
                                   long freshnessClosureRatePct) {
    }
}
