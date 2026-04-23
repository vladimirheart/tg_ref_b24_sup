package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogMacroGovernanceAuditService {

    private static final int DEFAULT_MACRO_GOVERNANCE_UNUSED_DAYS = 30;
    private static final long DEFAULT_MACRO_GOVERNANCE_REVIEW_TTL_HOURS = 24L * 90L;
    private static final long DEFAULT_MACRO_GOVERNANCE_CHECKPOINT_TTL_HOURS = 24L * 7L;

    private final DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService;

    public DialogMacroGovernanceAuditService(DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService) {
        this.dialogMacroGovernanceSupportService = dialogMacroGovernanceSupportService;
    }

    public Map<String, Object> buildAudit(Map<String, Object> settings) {
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
        var knownMacroVariables = dialogMacroGovernanceSupportService.resolveKnownMacroVariableKeys(dialogConfig);

        List<Map<String, Object>> auditedTemplates = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        int publishedActiveTotal = 0;
        int deprecatedTotal = 0;
        int missingOwnerTotal = 0;
        int missingNamespaceTotal = 0;
        int staleReviewTotal = 0;
        int invalidReviewTotal = 0;
        int unusedPublishedTotal = 0;
        int deprecationGapTotal = 0;
        int redListTotal = 0;
        int lowSignalRedListTotal = 0;
        int ownerActionTotal = 0;
        int aliasCleanupTotal = 0;
        int variableCleanupTotal = 0;
        int cleanupSlaOverdueTotal = 0;
        int deprecationSlaOverdueTotal = 0;
        List<String> lowSignalRedListTemplates = new ArrayList<>();

        for (Map<String, Object> template : templates) {
            String templateId = normalizeNullString(String.valueOf(template.get("id")));
            String templateName = normalizeNullString(String.valueOf(template.get("name")));
            String templateText = normalizeNullString(String.valueOf(template.get("message")));
            if (!StringUtils.hasText(templateText)) {
                templateText = normalizeNullString(String.valueOf(template.get("text")));
            }
            boolean published = toBoolean(template.get("published"));
            boolean deprecated = toBoolean(template.get("deprecated"));
            boolean activePublished = published && !deprecated;
            String owner = normalizeNullString(String.valueOf(template.get("owner")));
            String namespace = normalizeNullString(String.valueOf(template.get("namespace")));
            String reviewedAtRaw = normalizeNullString(String.valueOf(template.get("reviewed_at")));
            OffsetDateTime reviewedAt = parseReviewTimestamp(reviewedAtRaw);
            boolean reviewedAtInvalid = StringUtils.hasText(reviewedAtRaw) && reviewedAt == null;
            long reviewAgeHours = reviewedAt != null
                    ? Math.max(0L, java.time.Duration.between(reviewedAt, generatedAt).toHours())
                    : -1L;
            boolean reviewFresh = reviewedAt != null && reviewAgeHours <= reviewTtlHours;
            String deprecationReason = normalizeNullString(String.valueOf(template.get("deprecation_reason")));
            Map<String, Object> usage = dialogMacroGovernanceSupportService.loadMacroTemplateUsage(templateId, templateName, usageWindowDays);
            long usageCount = toLong(usage.get("usage_count"));
            long previewCount = toLong(usage.get("preview_count"));
            long errorCount = toLong(usage.get("error_count"));
            String lastUsedAt = normalizeUtcTimestamp(usage.get("last_used_at"));
            OffsetDateTime lastUsedAtUtc = parseReviewTimestamp(lastUsedAt);
            String deprecatedAtRaw = normalizeNullString(String.valueOf(template.get("deprecated_at")));
            OffsetDateTime deprecatedAtUtc = parseReviewTimestamp(deprecatedAtRaw);
            List<String> tagAliases = dialogMacroGovernanceSupportService.resolveMacroTagAliases(template.get("tags"));
            int duplicateAliasCount = Math.max(0, tagAliases.size() - new LinkedHashSet<>(tagAliases).size());
            List<String> usedVariables = dialogMacroGovernanceSupportService.extractMacroTemplateVariables(templateText);
            List<String> unknownVariables = usedVariables.stream()
                    .filter(variable -> !knownMacroVariables.contains(variable))
                    .distinct()
                    .toList();
            String usageTier = dialogMacroGovernanceSupportService.resolveMacroUsageTier(usageCount, usageTierLowMax, usageTierMediumMax);
            int cleanupSlaDays = dialogMacroGovernanceSupportService.resolveMacroTierSlaDays(usageTier, cleanupSlaLowDays, cleanupSlaMediumDays, cleanupSlaHighDays);
            int deprecationSlaDays = dialogMacroGovernanceSupportService.resolveMacroTierSlaDays(usageTier, deprecationSlaLowDays, deprecationSlaMediumDays, deprecationSlaHighDays);
            OffsetDateTime cleanupReferenceAt = lastUsedAtUtc != null ? lastUsedAtUtc : (reviewedAt != null ? reviewedAt : generatedAt);
            long cleanupDueInDays = java.time.Duration.between(generatedAt, cleanupReferenceAt.plusDays(cleanupSlaDays)).toDays();
            String cleanupSlaStatus = !activePublished ? "off" : (cleanupDueInDays < 0 ? "hold" : "attention");
            OffsetDateTime deprecationReferenceAt = deprecatedAtUtc != null ? deprecatedAtUtc : generatedAt;
            long deprecationDueInDays = java.time.Duration.between(generatedAt, deprecationReferenceAt.plusDays(deprecationSlaDays)).toDays();
            String deprecationSlaStatus = !deprecated ? "off" : (deprecationDueInDays < 0 ? "hold" : "attention");

            if (activePublished) {
                publishedActiveTotal += 1;
            }
            if (deprecated) {
                deprecatedTotal += 1;
            }

            List<String> templateIssues = new ArrayList<>();
            boolean hasBlockingIssue = false;
            if (activePublished && !StringUtils.hasText(owner)) {
                missingOwnerTotal += 1;
                templateIssues.add("owner_missing");
                hasBlockingIssue = requireOwner;
                issues.add(buildMacroGovernanceIssue("owner_missing", templateId, templateName, requireOwner ? "hold" : "attention", requireOwner ? "rollout_blocker" : "backlog_candidate", "У опубликованного макроса отсутствует owner.", "owner=missing"));
            }
            if (activePublished && !StringUtils.hasText(namespace)) {
                missingNamespaceTotal += 1;
                templateIssues.add("namespace_missing");
                hasBlockingIssue = hasBlockingIssue || requireNamespace;
                issues.add(buildMacroGovernanceIssue("namespace_missing", templateId, templateName, requireNamespace ? "hold" : "attention", requireNamespace ? "rollout_blocker" : "backlog_candidate", "У опубликованного макроса отсутствует namespace.", "namespace=missing"));
            }
            if (activePublished && reviewedAtInvalid) {
                invalidReviewTotal += 1;
                templateIssues.add("review_invalid_utc");
                hasBlockingIssue = hasBlockingIssue || requireReview;
                issues.add(buildMacroGovernanceIssue("review_invalid_utc", templateId, templateName, requireReview ? "hold" : "attention", requireReview ? "rollout_blocker" : "backlog_candidate", "Дата review макроса невалидна и не может быть интерпретирована как UTC.", "reviewed_at=invalid"));
            }
            if (activePublished && requireReview && (!reviewFresh || reviewedAt == null || reviewedAtInvalid)) {
                staleReviewTotal += 1;
                templateIssues.add(reviewedAt == null ? "review_missing" : "review_stale");
                hasBlockingIssue = true;
                issues.add(buildMacroGovernanceIssue(
                        reviewedAt == null ? "review_missing" : "review_stale",
                        templateId,
                        templateName,
                        "hold",
                        "rollout_blocker",
                        reviewedAt == null ? "У опубликованного макроса нет review-signoff." : "Review макроса устарел и требует повторной проверки.",
                        reviewedAt == null ? "reviewed_at=missing" : "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, reviewTtlHours)));
            }
            if (activePublished && usageCount <= 0) {
                unusedPublishedTotal += 1;
                templateIssues.add("unused_recently");
                issues.add(buildMacroGovernanceIssue("unused_recently", templateId, templateName, "attention", "backlog_candidate", "Опубликованный макрос не использовался в telemetry окне и требует cleanup-review.", "window_days=%d".formatted(usageWindowDays)));
            }
            List<String> redListReasons = new ArrayList<>();
            if (redListEnabled && activePublished && usageCount <= redListUsageMax) {
                redListReasons.add("low_adoption");
            }
            if (redListEnabled && activePublished && previewCount > 0 && usageCount == 0) {
                redListReasons.add("preview_only");
            }
            if (redListEnabled && activePublished && errorCount > 0) {
                redListReasons.add("runtime_errors");
            }
            boolean redListCandidate = !redListReasons.isEmpty();
            boolean lowSignalRedListCandidate = redListCandidate
                    && redListReasons.stream().allMatch("low_adoption"::equals)
                    && usageCount > 0
                    && previewCount == 0
                    && errorCount == 0;
            if (redListCandidate) {
                redListTotal += 1;
                if (lowSignalRedListCandidate) {
                    lowSignalRedListTotal += 1;
                    lowSignalRedListTemplates.add(StringUtils.hasText(templateName) ? templateName : templateId);
                }
                templateIssues.add("red_list_candidate");
                issues.add(buildMacroGovernanceIssue("red_list_candidate", templateId, templateName, ownerActionRequired ? "hold" : "attention", ownerActionRequired ? "rollout_blocker" : "backlog_candidate", "Макрос попал в quality red-list и требует owner review.", "reasons=%s".formatted(String.join(",", redListReasons))));
            }
            if (aliasCleanupRequired && activePublished && duplicateAliasCount > 0) {
                aliasCleanupTotal += 1;
                templateIssues.add("alias_cleanup_required");
                issues.add(buildMacroGovernanceIssue("alias_cleanup_required", templateId, templateName, "attention", "backlog_candidate", "У macro template есть дублирующиеся aliases/tags и нужен cleanup.", "duplicate_aliases=%d".formatted(duplicateAliasCount)));
            }
            if (variableCleanupRequired && activePublished && !unknownVariables.isEmpty()) {
                variableCleanupTotal += 1;
                templateIssues.add("unknown_variables_detected");
                issues.add(buildMacroGovernanceIssue("unknown_variables_detected", templateId, templateName, "attention", "backlog_candidate", "В macro template есть переменные вне известного каталога.", "unknown_variables=%s".formatted(String.join(",", unknownVariables))));
            }
            boolean ownerActionNeeded = ownerActionRequired
                    && activePublished
                    && (redListCandidate || (aliasCleanupRequired && duplicateAliasCount > 0)
                    || (variableCleanupRequired && !unknownVariables.isEmpty()));
            long ownerActionDueInDays = Long.MIN_VALUE;
            String ownerActionStatus = "off";
            if (ownerActionNeeded) {
                ownerActionTotal += 1;
                OffsetDateTime dueReference = lastUsedAtUtc != null ? lastUsedAtUtc : reviewedAt;
                if (!StringUtils.hasText(owner)) {
                    ownerActionStatus = "hold";
                } else if (cleanupCadenceDays <= 0) {
                    ownerActionStatus = "attention";
                } else if (dueReference == null) {
                    ownerActionStatus = "hold";
                } else {
                    ownerActionDueInDays = java.time.Duration.between(generatedAt, dueReference.plusDays(cleanupCadenceDays)).toDays();
                    ownerActionStatus = ownerActionDueInDays < 0 ? "hold" : "attention";
                }
                templateIssues.add("owner_action_required");
                issues.add(buildMacroGovernanceIssue(
                        "owner_action_required",
                        templateId,
                        templateName,
                        ownerActionStatus,
                        "hold".equals(ownerActionStatus) ? "rollout_blocker" : "backlog_candidate",
                        "Для проблемного macro template требуется owner action.",
                        cleanupCadenceDays > 0
                                ? (ownerActionDueInDays == Long.MIN_VALUE ? "owner_action_due=unknown" : "owner_action_due_in_days=%d".formatted(ownerActionDueInDays))
                                : "owner_action_required"));
            }
            if (deprecated && deprecationRequiresReason && !StringUtils.hasText(deprecationReason)) {
                deprecationGapTotal += 1;
                templateIssues.add("deprecation_reason_missing");
                issues.add(buildMacroGovernanceIssue("deprecation_reason_missing", templateId, templateName, "attention", "backlog_candidate", "Для deprecated макроса не указана причина вывода из эксплуатации.", "deprecation_reason=missing"));
            }
            if (usageTierSlaRequired && activePublished && cleanupDueInDays < 0) {
                cleanupSlaOverdueTotal += 1;
                templateIssues.add("cleanup_sla_overdue");
                issues.add(buildMacroGovernanceIssue("cleanup_sla_overdue", templateId, templateName, "hold", "rollout_blocker", "Cleanup SLA для macro template просрочен.", "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(cleanupDueInDays))));
            }
            if (usageTierSlaRequired && deprecated && deprecationDueInDays < 0) {
                deprecationSlaOverdueTotal += 1;
                templateIssues.add("deprecation_sla_overdue");
                issues.add(buildMacroGovernanceIssue("deprecation_sla_overdue", templateId, templateName, "hold", "rollout_blocker", "Deprecation SLA для macro template просрочен.", "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(deprecationDueInDays))));
            }

            Map<String, Object> auditTemplate = new LinkedHashMap<>();
            auditTemplate.put("template_id", templateId);
            auditTemplate.put("template_name", templateName);
            auditTemplate.put("status", deprecated ? "off" : (hasBlockingIssue ? "hold" : (templateIssues.isEmpty() ? "ok" : "attention")));
            auditTemplate.put("published", published);
            auditTemplate.put("deprecated", deprecated);
            auditTemplate.put("owner", owner);
            auditTemplate.put("namespace", namespace);
            auditTemplate.put("reviewed_at_utc", reviewedAt != null ? reviewedAt.toString() : "");
            auditTemplate.put("reviewed_at_invalid_utc", reviewedAtInvalid);
            auditTemplate.put("review_age_hours", reviewAgeHours);
            auditTemplate.put("usage_count", usageCount);
            auditTemplate.put("preview_count", previewCount);
            auditTemplate.put("error_count", errorCount);
            auditTemplate.put("last_used_at_utc", lastUsedAt);
            auditTemplate.put("deprecated_at_utc", deprecatedAtUtc != null ? deprecatedAtUtc.toString() : "");
            auditTemplate.put("usage_tier", usageTier);
            auditTemplate.put("cleanup_sla_days", cleanupSlaDays);
            auditTemplate.put("cleanup_due_in_days", cleanupDueInDays);
            auditTemplate.put("cleanup_sla_status", cleanupSlaStatus);
            auditTemplate.put("deprecation_sla_days", deprecationSlaDays);
            auditTemplate.put("deprecation_due_in_days", deprecated ? deprecationDueInDays : -1L);
            auditTemplate.put("deprecation_sla_status", deprecationSlaStatus);
            auditTemplate.put("red_list_candidate", redListCandidate);
            auditTemplate.put("red_list_low_signal", lowSignalRedListCandidate);
            auditTemplate.put("red_list_reasons", redListReasons);
            auditTemplate.put("owner_action_required", ownerActionNeeded);
            auditTemplate.put("owner_action_status", ownerActionStatus);
            auditTemplate.put("owner_action_due_in_days", ownerActionDueInDays == Long.MIN_VALUE ? -1L : ownerActionDueInDays);
            auditTemplate.put("alias_count", tagAliases.size());
            auditTemplate.put("duplicate_alias_count", duplicateAliasCount);
            auditTemplate.put("used_variables", usedVariables);
            auditTemplate.put("unknown_variables", unknownVariables);
            auditTemplate.put("unknown_variable_count", unknownVariables.size());
            auditTemplate.put("deprecation_reason", deprecationReason);
            auditTemplate.put("issues", templateIssues);
            auditedTemplates.add(auditTemplate);
        }

        boolean governanceReviewRequired = resolveBooleanConfig(dialogConfig, "macro_governance_review_required", false);
        long governanceReviewTtlHours = resolveLongConfig(dialogConfig, "macro_governance_checkpoint_ttl_hours", DEFAULT_MACRO_GOVERNANCE_CHECKPOINT_TTL_HOURS, 1, 24L * 90L);
        boolean governanceCleanupTicketRequired = resolveBooleanConfig(dialogConfig, "macro_governance_cleanup_ticket_required", false);
        String governanceReviewedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_by")));
        String governanceReviewedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_at")));
        OffsetDateTime governanceReviewedAt = parseReviewTimestamp(governanceReviewedAtRaw);
        boolean governanceReviewedAtInvalid = StringUtils.hasText(governanceReviewedAtRaw) && governanceReviewedAt == null;
        long governanceReviewAgeHours = governanceReviewedAt != null
                ? Math.max(0L, java.time.Duration.between(governanceReviewedAt, generatedAt).toHours())
                : -1L;
        boolean governanceReviewFresh = governanceReviewedAt != null && governanceReviewAgeHours <= governanceReviewTtlHours;
        String governanceReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_review_note")));
        String governanceDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_review_decision")));
        if (governanceDecision != null) {
            governanceDecision = governanceDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(governanceDecision) && !"hold".equals(governanceDecision)) {
                governanceDecision = null;
            }
        }
        String governanceCleanupTicketId = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_cleanup_ticket_id")));
        boolean governanceReady = !governanceReviewRequired || (StringUtils.hasText(governanceReviewedBy)
                && governanceReviewFresh
                && !governanceReviewedAtInvalid
                && (!governanceCleanupTicketRequired || StringUtils.hasText(governanceCleanupTicketId)));
        List<String> governanceReviewIssues = new ArrayList<>();
        if (governanceReviewRequired) {
            if (!StringUtils.hasText(governanceReviewedBy) || governanceReviewedAt == null) {
                governanceReviewIssues.add("governance_review_missing");
                issues.add(buildMacroGovernanceIssue("governance_review_missing", null, null, "hold", "rollout_blocker", "Macro governance review checkpoint не заполнен.", "reviewed_by/reviewed_at=missing"));
            } else if (governanceReviewedAtInvalid) {
                governanceReviewIssues.add("governance_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue("governance_review_invalid_utc", null, null, "hold", "rollout_blocker", "Дата macro governance review невалидна и не может быть интерпретирована как UTC.", "reviewed_at=invalid"));
            } else if (!governanceReviewFresh) {
                governanceReviewIssues.add("governance_review_stale");
                issues.add(buildMacroGovernanceIssue("governance_review_stale", null, null, "hold", "rollout_blocker", "Macro governance review устарел и требует повторной проверки.", "review_age_hours=%d > ttl=%d".formatted(governanceReviewAgeHours, governanceReviewTtlHours)));
            }
            if (governanceCleanupTicketRequired && !StringUtils.hasText(governanceCleanupTicketId)) {
                governanceReviewIssues.add("governance_cleanup_ticket_missing");
                issues.add(buildMacroGovernanceIssue("governance_cleanup_ticket_missing", null, null, "attention", "backlog_candidate", "Для macro governance review требуется cleanup ticket.", "cleanup_ticket_id=missing"));
            }
        }

        boolean externalCatalogContractRequired = resolveBooleanConfig(dialogConfig, "macro_external_catalog_contract_required", false);
        long externalCatalogContractTtlHours = resolveLongConfig(dialogConfig, "macro_external_catalog_contract_ttl_hours", 168, 1, 24L * 90L);
        String externalCatalogExpectedVersion = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_expected_version")));
        String externalCatalogObservedVersion = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_observed_version")));
        String externalCatalogVerifiedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_by")));
        String externalCatalogVerifiedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_at")));
        OffsetDateTime externalCatalogVerifiedAt = parseReviewTimestamp(externalCatalogVerifiedAtRaw);
        boolean externalCatalogVerifiedAtInvalid = StringUtils.hasText(externalCatalogVerifiedAtRaw) && externalCatalogVerifiedAt == null;
        long externalCatalogReviewAgeHours = externalCatalogVerifiedAt != null
                ? Math.max(0L, java.time.Duration.between(externalCatalogVerifiedAt, generatedAt).toHours())
                : -1L;
        boolean externalCatalogReviewFresh = externalCatalogVerifiedAt != null && externalCatalogReviewAgeHours <= externalCatalogContractTtlHours;
        String externalCatalogReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_review_note")));
        String externalCatalogDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_decision")));
        if (externalCatalogDecision != null) {
            externalCatalogDecision = externalCatalogDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(externalCatalogDecision) && !"hold".equals(externalCatalogDecision)) {
                externalCatalogDecision = null;
            }
        }
        List<String> externalCatalogIssues = new ArrayList<>();
        if (externalCatalogContractRequired) {
            if (!StringUtils.hasText(externalCatalogExpectedVersion)) {
                externalCatalogIssues.add("external_catalog_expected_version_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_expected_version_missing", null, null, "hold", "rollout_blocker", "Не задан expected version для external macro catalog.", "expected_version=missing"));
            }
            if (!StringUtils.hasText(externalCatalogObservedVersion)) {
                externalCatalogIssues.add("external_catalog_observed_version_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_observed_version_missing", null, null, "hold", "rollout_blocker", "Не зафиксирована observed version для external macro catalog.", "observed_version=missing"));
            }
            if (StringUtils.hasText(externalCatalogExpectedVersion) && StringUtils.hasText(externalCatalogObservedVersion)
                    && !externalCatalogExpectedVersion.equalsIgnoreCase(externalCatalogObservedVersion)) {
                externalCatalogIssues.add("external_catalog_version_mismatch");
                issues.add(buildMacroGovernanceIssue("external_catalog_version_mismatch", null, null, "hold", "rollout_blocker", "Observed version external macro catalog не совпадает с ожидаемой.", "expected=%s observed=%s".formatted(externalCatalogExpectedVersion, externalCatalogObservedVersion)));
            }
            if (!StringUtils.hasText(externalCatalogVerifiedBy) || externalCatalogVerifiedAt == null) {
                externalCatalogIssues.add("external_catalog_review_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_missing", null, null, "hold", "rollout_blocker", "External catalog compatibility review не заполнен.", "verified_by/verified_at=missing"));
            } else if (externalCatalogVerifiedAtInvalid) {
                externalCatalogIssues.add("external_catalog_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_invalid_utc", null, null, "hold", "rollout_blocker", "Дата compatibility review external macro catalog невалидна для UTC.", "verified_at=invalid"));
            } else if (!externalCatalogReviewFresh) {
                externalCatalogIssues.add("external_catalog_review_stale");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_stale", null, null, "hold", "rollout_blocker", "External catalog compatibility review устарел.", "review_age_hours=%d > ttl=%d".formatted(externalCatalogReviewAgeHours, externalCatalogContractTtlHours)));
            }
        }
        boolean externalCatalogReady = !externalCatalogContractRequired || externalCatalogIssues.isEmpty();
        boolean deprecationPolicyRequired = resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_required", false);
        long deprecationPolicyTtlHours = resolveLongConfig(dialogConfig, "macro_deprecation_policy_ttl_hours", 168, 1, 24L * 90L);
        boolean deprecationPolicyTicketRequired = resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_ticket_required", false);
        String deprecationPolicyReviewedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_by")));
        String deprecationPolicyReviewedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_at")));
        OffsetDateTime deprecationPolicyReviewedAt = parseReviewTimestamp(deprecationPolicyReviewedAtRaw);
        boolean deprecationPolicyReviewedAtInvalid = StringUtils.hasText(deprecationPolicyReviewedAtRaw) && deprecationPolicyReviewedAt == null;
        long deprecationPolicyReviewAgeHours = deprecationPolicyReviewedAt != null
                ? Math.max(0L, java.time.Duration.between(deprecationPolicyReviewedAt, generatedAt).toHours())
                : -1L;
        boolean deprecationPolicyReviewFresh = deprecationPolicyReviewedAt != null && deprecationPolicyReviewAgeHours <= deprecationPolicyTtlHours;
        String deprecationPolicyDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_decision")));
        if (deprecationPolicyDecision != null) {
            deprecationPolicyDecision = deprecationPolicyDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(deprecationPolicyDecision) && !"hold".equals(deprecationPolicyDecision)) {
                deprecationPolicyDecision = null;
            }
        }
        String deprecationPolicyReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_review_note")));
        String deprecationPolicyTicketId = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_ticket_id")));
        List<String> deprecationPolicyIssues = new ArrayList<>();
        if (deprecationPolicyRequired) {
            if (!StringUtils.hasText(deprecationPolicyReviewedBy) || deprecationPolicyReviewedAt == null) {
                deprecationPolicyIssues.add("deprecation_policy_review_missing");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_missing", null, null, "hold", "rollout_blocker", "Macro deprecation policy review checkpoint не заполнен.", "reviewed_by/reviewed_at=missing"));
            } else if (deprecationPolicyReviewedAtInvalid) {
                deprecationPolicyIssues.add("deprecation_policy_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_invalid_utc", null, null, "hold", "rollout_blocker", "Дата macro deprecation policy review невалидна для UTC.", "reviewed_at=invalid"));
            } else if (!deprecationPolicyReviewFresh) {
                deprecationPolicyIssues.add("deprecation_policy_review_stale");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_stale", null, null, "hold", "rollout_blocker", "Macro deprecation policy review устарел.", "review_age_hours=%d > ttl=%d".formatted(deprecationPolicyReviewAgeHours, deprecationPolicyTtlHours)));
            }
            if (deprecationPolicyTicketRequired && !StringUtils.hasText(deprecationPolicyTicketId)) {
                deprecationPolicyIssues.add("deprecation_policy_ticket_missing");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_ticket_missing", null, null, "attention", "backlog_candidate", "Для deprecation policy checkpoint требуется deprecation ticket.", "deprecation_ticket_id=missing"));
            }
        }
        boolean deprecationPolicyReady = !deprecationPolicyRequired || deprecationPolicyIssues.isEmpty();

        String status;
        if (templates.isEmpty()) {
            status = "off";
        } else if (issues.stream().anyMatch(item -> "hold".equalsIgnoreCase(String.valueOf(item.get("status"))))) {
            status = "hold";
        } else if (!issues.isEmpty()) {
            status = "attention";
        } else {
            status = "ok";
        }
        long mandatoryIssueTotal = issues.stream().filter(item -> "rollout_blocker".equals(String.valueOf(item.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long reviewIssueTotal = issues.stream().filter(item -> String.valueOf(item.get("type")).contains("review")).count();
        long ownershipIssueTotal = issues.stream().filter(item -> String.valueOf(item.get("type")).contains("owner") || String.valueOf(item.get("type")).contains("namespace")).count();
        long cleanupIssueTotal = issues.stream().filter(item -> String.valueOf(item.get("type")).contains("cleanup") || String.valueOf(item.get("type")).contains("deprecation") || String.valueOf(item.get("type")).contains("alias") || String.valueOf(item.get("type")).contains("variable")).count();
        List<String> minimumRequiredCheckpoints = new ArrayList<>();
        if (governanceReviewRequired) minimumRequiredCheckpoints.add("governance_review");
        if (externalCatalogContractRequired) minimumRequiredCheckpoints.add("external_catalog");
        if (deprecationPolicyRequired && minimumRequiredCheckpoints.size() < 2) minimumRequiredCheckpoints.add("deprecation_policy");
        if (minimumRequiredCheckpoints.isEmpty() && requireOwner) minimumRequiredCheckpoints.add("template_owner");
        List<String> advisorySignals = new ArrayList<>();
        if (redListEnabled) advisorySignals.add("red_list");
        if (ownerActionRequired) advisorySignals.add("owner_action");
        if (aliasCleanupRequired) advisorySignals.add("alias_cleanup");
        if (variableCleanupRequired) advisorySignals.add("variable_cleanup");
        if (usageTierSlaRequired) advisorySignals.add("usage_tier_sla");
        Map<String, Boolean> requiredCheckpointState = new LinkedHashMap<>();
        requiredCheckpointState.put("governance_review", governanceReady);
        requiredCheckpointState.put("external_catalog", externalCatalogReady);
        requiredCheckpointState.put("deprecation_policy", deprecationPolicyReady);
        requiredCheckpointState.put("template_owner", missingOwnerTotal == 0);
        long requiredCheckpointTotal = minimumRequiredCheckpoints.size();
        long requiredCheckpointReadyTotal = minimumRequiredCheckpoints.stream().filter(key -> Boolean.TRUE.equals(requiredCheckpointState.get(key))).count();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0 ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal) : 100L;
        long freshnessCheckpointTotal = java.util.stream.Stream.of(governanceReviewRequired, externalCatalogContractRequired, deprecationPolicyRequired).filter(Boolean::booleanValue).count();
        long freshnessCheckpointReadyTotal = 0L;
        if (governanceReviewRequired && governanceReviewedAt != null && governanceReviewFresh && !governanceReviewedAtInvalid) freshnessCheckpointReadyTotal += 1L;
        if (externalCatalogContractRequired && externalCatalogVerifiedAt != null && externalCatalogReviewFresh && !externalCatalogVerifiedAtInvalid) freshnessCheckpointReadyTotal += 1L;
        if (deprecationPolicyRequired && deprecationPolicyReviewedAt != null && deprecationPolicyReviewFresh && !deprecationPolicyReviewedAtInvalid) freshnessCheckpointReadyTotal += 1L;
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0 ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal) : 100L;
        long lowSignalAdvisoryTotal = lowSignalRedListTotal;
        long actionableAdvisoryTotal = Math.max(0L, advisoryIssueTotal - lowSignalAdvisoryTotal);
        long noiseRatioPct = issues.isEmpty() ? 0L : Math.round((advisoryIssueTotal * 100d) / issues.size());
        long advisoryNoiseExcludingLowSignalPct = issues.isEmpty() ? 0L : Math.round((actionableAdvisoryTotal * 100d) / issues.size());
        long actionableAdvisorySharePct = advisoryIssueTotal > 0 ? Math.round((actionableAdvisoryTotal * 100d) / advisoryIssueTotal) : 0L;
        long lowSignalAdvisorySharePct = advisoryIssueTotal > 0 ? Math.round((lowSignalAdvisoryTotal * 100d) / advisoryIssueTotal) : 0L;
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal ? "controlled" : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L) ? "high" : "moderate";
        boolean advisoryFollowupRequired = actionableAdvisoryTotal > mandatoryIssueTotal || advisoryNoiseExcludingLowSignalPct >= 50L;
        boolean lowSignalBacklogDominant = lowSignalAdvisoryTotal > actionableAdvisoryTotal && lowSignalAdvisorySharePct >= 50L;
        boolean minimumRequiredPathControlled = requiredCheckpointClosureRatePct >= 100L && freshnessClosureRatePct >= 100L;
        String lowSignalBacklogSummary = lowSignalBacklogDominant ? "Low-signal red-list начинает доминировать над actionable advisory; держите его аналитическим, а не backlog-driven." : "Low-signal red-list не доминирует над actionable advisory.";
        String weeklyReviewPriority = requiredCheckpointClosureRatePct < 100L ? "close_required_path" : freshnessClosureRatePct < 100L ? "refresh_stale_checkpoints" : actionableAdvisoryTotal == 0 && lowSignalAdvisoryTotal > 0 ? "monitor_low_signal_advisories" : "high".equals(noiseLevel) ? "reduce_advisory_noise" : advisoryFollowupRequired ? "trim_advisory_noise" : "monitor";
        String weeklyReviewSummary = switch (weeklyReviewPriority) {
            case "close_required_path" -> "Сначала закройте обязательные macro checkpoints.";
            case "refresh_stale_checkpoints" -> "Освежите review/catalog/deprecation checkpoints по UTC TTL.";
            case "monitor_low_signal_advisories" -> "Advisory шум в основном состоит из low-signal red-list кандидатов; держите их аналитическими, а не бюрократическими.";
            case "reduce_advisory_noise" -> "Сократите advisory red-list шум до минимального обязательного контура.";
            case "trim_advisory_noise" -> "Проверьте, что advisory сигналы не доминируют над обязательными.";
            default -> "Closure, freshness и noise находятся в рабочем диапазоне.";
        };
        boolean weeklyReviewFollowupRequired = !"monitor".equals(weeklyReviewPriority) && !"monitor_low_signal_advisories".equals(weeklyReviewPriority);
        boolean advisoryPathReductionCandidate = "reduce_advisory_noise".equals(weeklyReviewPriority) || "trim_advisory_noise".equals(weeklyReviewPriority);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generated_at", generatedAt.toInstant().toString());
        audit.put("status", status);
        audit.put("summary", templates.isEmpty() ? "Macro governance audit недоступен: макросы не настроены." : "Published active=%d, deprecated=%d, issues=%d, red-list=%d.".formatted(publishedActiveTotal, deprecatedTotal, issues.size(), redListTotal));
        audit.put("templates_total", templates.size());
        audit.put("published_active_total", publishedActiveTotal);
        audit.put("deprecated_total", deprecatedTotal);
        audit.put("issues_total", issues.size());
        audit.put("mandatory_issue_total", mandatoryIssueTotal);
        audit.put("advisory_issue_total", advisoryIssueTotal);
        audit.put("low_signal_advisory_total", lowSignalAdvisoryTotal);
        audit.put("actionable_advisory_total", actionableAdvisoryTotal);
        audit.put("actionable_advisory_share_pct", actionableAdvisorySharePct);
        audit.put("low_signal_advisory_share_pct", lowSignalAdvisorySharePct);
        audit.put("low_signal_backlog_dominant", lowSignalBacklogDominant);
        audit.put("low_signal_backlog_summary", lowSignalBacklogSummary);
        audit.put("missing_owner_total", missingOwnerTotal);
        audit.put("missing_namespace_total", missingNamespaceTotal);
        audit.put("stale_review_total", staleReviewTotal);
        audit.put("invalid_review_total", invalidReviewTotal);
        audit.put("unused_published_total", unusedPublishedTotal);
        audit.put("deprecation_gap_total", deprecationGapTotal);
        audit.put("red_list_total", redListTotal);
        audit.put("owner_action_total", ownerActionTotal);
        audit.put("alias_cleanup_total", aliasCleanupTotal);
        audit.put("variable_cleanup_total", variableCleanupTotal);
        audit.put("cleanup_sla_overdue_total", cleanupSlaOverdueTotal);
        audit.put("deprecation_sla_overdue_total", deprecationSlaOverdueTotal);
        audit.put("minimum_required_checkpoints", minimumRequiredCheckpoints);
        audit.put("required_checkpoint_total", requiredCheckpointTotal);
        audit.put("required_checkpoint_ready_total", requiredCheckpointReadyTotal);
        audit.put("required_checkpoint_closure_rate_pct", requiredCheckpointClosureRatePct);
        audit.put("minimum_required_path_controlled", minimumRequiredPathControlled);
        audit.put("freshness_checkpoint_total", freshnessCheckpointTotal);
        audit.put("freshness_checkpoint_ready_total", freshnessCheckpointReadyTotal);
        audit.put("freshness_closure_rate_pct", freshnessClosureRatePct);
        audit.put("noise_ratio_pct", noiseRatioPct);
        audit.put("advisory_noise_excluding_low_signal_pct", advisoryNoiseExcludingLowSignalPct);
        audit.put("noise_level", noiseLevel);
        audit.put("advisory_followup_required", advisoryFollowupRequired);
        audit.put("weekly_review_priority", weeklyReviewPriority);
        audit.put("weekly_review_summary", weeklyReviewSummary);
        audit.put("weekly_review_followup_required", weeklyReviewFollowupRequired);
        audit.put("advisory_path_reduction_candidate", advisoryPathReductionCandidate);
        audit.put("advisory_signals", advisorySignals.stream().distinct().toList());
        audit.put("low_signal_red_list_templates", lowSignalRedListTemplates.stream().distinct().limit(5).toList());
        audit.put("issue_breakdown", Map.of("review", reviewIssueTotal, "ownership", ownershipIssueTotal, "cleanup", cleanupIssueTotal, "mandatory", mandatoryIssueTotal, "advisory", advisoryIssueTotal));
        audit.put("requirements", Map.ofEntries(
                Map.entry("require_owner", requireOwner),
                Map.entry("require_namespace", requireNamespace),
                Map.entry("require_review", requireReview),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("deprecation_requires_reason", deprecationRequiresReason),
                Map.entry("unused_days", usageWindowDays),
                Map.entry("red_list_enabled", redListEnabled),
                Map.entry("red_list_usage_max", redListUsageMax),
                Map.entry("owner_action_required", ownerActionRequired),
                Map.entry("cleanup_cadence_days", cleanupCadenceDays),
                Map.entry("alias_cleanup_required", aliasCleanupRequired),
                Map.entry("variable_cleanup_required", variableCleanupRequired),
                Map.entry("usage_tier_sla_required", usageTierSlaRequired),
                Map.entry("usage_tier_low_max", usageTierLowMax),
                Map.entry("usage_tier_medium_max", usageTierMediumMax),
                Map.entry("cleanup_sla_low_days", cleanupSlaLowDays),
                Map.entry("cleanup_sla_medium_days", cleanupSlaMediumDays),
                Map.entry("cleanup_sla_high_days", cleanupSlaHighDays),
                Map.entry("deprecation_sla_low_days", deprecationSlaLowDays),
                Map.entry("deprecation_sla_medium_days", deprecationSlaMediumDays),
                Map.entry("deprecation_sla_high_days", deprecationSlaHighDays)));
        audit.put("governance_review", Map.ofEntries(
                Map.entry("required", governanceReviewRequired),
                Map.entry("ready", governanceReady),
                Map.entry("reviewed_by", governanceReviewedBy),
                Map.entry("reviewed_at_utc", governanceReviewedAt == null ? "" : governanceReviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", governanceReviewedAtInvalid),
                Map.entry("review_ttl_hours", governanceReviewTtlHours),
                Map.entry("review_age_hours", governanceReviewAgeHours),
                Map.entry("cleanup_ticket_required", governanceCleanupTicketRequired),
                Map.entry("cleanup_ticket_id", governanceCleanupTicketId),
                Map.entry("decision", governanceDecision == null ? "" : governanceDecision),
                Map.entry("review_note", governanceReviewNote),
                Map.entry("issues", governanceReviewIssues)));
        audit.put("external_catalog_contract", Map.ofEntries(
                Map.entry("required", externalCatalogContractRequired),
                Map.entry("ready", externalCatalogReady),
                Map.entry("expected_version", externalCatalogExpectedVersion),
                Map.entry("observed_version", externalCatalogObservedVersion),
                Map.entry("verified_by", externalCatalogVerifiedBy),
                Map.entry("verified_at_utc", externalCatalogVerifiedAt == null ? "" : externalCatalogVerifiedAt.toString()),
                Map.entry("verified_at_invalid_utc", externalCatalogVerifiedAtInvalid),
                Map.entry("review_ttl_hours", externalCatalogContractTtlHours),
                Map.entry("review_age_hours", externalCatalogReviewAgeHours),
                Map.entry("decision", externalCatalogDecision == null ? "" : externalCatalogDecision),
                Map.entry("review_note", externalCatalogReviewNote),
                Map.entry("issues", externalCatalogIssues)));
        audit.put("deprecation_policy", Map.ofEntries(
                Map.entry("required", deprecationPolicyRequired),
                Map.entry("ready", deprecationPolicyReady),
                Map.entry("reviewed_by", deprecationPolicyReviewedBy),
                Map.entry("reviewed_at_utc", deprecationPolicyReviewedAt == null ? "" : deprecationPolicyReviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", deprecationPolicyReviewedAtInvalid),
                Map.entry("review_ttl_hours", deprecationPolicyTtlHours),
                Map.entry("review_age_hours", deprecationPolicyReviewAgeHours),
                Map.entry("deprecation_ticket_required", deprecationPolicyTicketRequired),
                Map.entry("deprecation_ticket_id", deprecationPolicyTicketId),
                Map.entry("decision", deprecationPolicyDecision == null ? "" : deprecationPolicyDecision),
                Map.entry("review_note", deprecationPolicyReviewNote),
                Map.entry("issues", deprecationPolicyIssues)));
        audit.put("issues", issues);
        audit.put("templates", auditedTemplates);
        return audit;
    }

    private Map<String, Object> buildMacroGovernanceIssue(String type,
                                                          String templateId,
                                                          String templateName,
                                                          String status,
                                                          String classification,
                                                          String summary,
                                                          String detail) {
        return dialogMacroGovernanceSupportService.buildMacroGovernanceIssue(type, templateId, templateName, status, classification, summary, detail);
    }

    private long resolveLongConfig(Map<String, Object> source, String key, long fallback, long minInclusive, long maxInclusive) {
        if (source == null || source.isEmpty()) return fallback;
        Object raw = source.get(key);
        if (raw == null) return fallback;
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

    private boolean resolveBooleanConfig(Map<String, Object> source, String key, boolean fallback) {
        if (source == null || source.isEmpty()) return fallback;
        Object raw = source.get(key);
        return raw == null ? fallback : toBoolean(raw);
    }

    private List<Map<String, Object>> safeListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(castObjectMap(map));
        }
        return result;
    }

    private Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        if (value == null) return false;
        String normalized = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return 0L;
    }

    private String normalizeNullString(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) return "";
        return value.trim();
    }

    private OffsetDateTime parseReviewTimestamp(String rawValue) {
        String value = normalizeNullString(rawValue);
        if (!StringUtils.hasText(value)) return null;
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        OffsetDateTime parsed = parseReviewTimestamp(rawValue == null ? null : String.valueOf(rawValue));
        return parsed != null ? parsed.toString() : "";
    }

}
