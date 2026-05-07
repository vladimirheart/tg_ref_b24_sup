package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DialogMacroGovernanceTemplateAuditService {

    private final DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService;
    private final DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService;

    public DialogMacroGovernanceTemplateAuditService(DialogMacroGovernanceSupportService dialogMacroGovernanceSupportService,
                                                     DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService) {
        this.dialogMacroGovernanceSupportService = dialogMacroGovernanceSupportService;
        this.dialogMacroGovernanceConfigService = dialogMacroGovernanceConfigService;
    }

    public TemplateAuditBundle audit(DialogMacroGovernanceConfigService.AuditConfig config) {
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

        for (Map<String, Object> template : config.templates()) {
            String templateId = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("id")));
            String templateName = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("name")));
            String templateText = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("message")));
            if (!StringUtils.hasText(templateText)) {
                templateText = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("text")));
            }
            boolean published = dialogMacroGovernanceConfigService.toBoolean(template.get("published"))
                    || dialogMacroGovernanceConfigService.toBoolean(template.get("approved_for_publish"));
            boolean deprecated = dialogMacroGovernanceConfigService.toBoolean(template.get("deprecated"));
            boolean activePublished = !deprecated;
            String owner = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("owner")));
            String namespace = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("namespace")));
            String reviewedAtRaw = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("reviewed_at")));
            OffsetDateTime reviewedAt = dialogMacroGovernanceConfigService.parseReviewTimestamp(reviewedAtRaw);
            boolean reviewedAtInvalid = StringUtils.hasText(reviewedAtRaw) && reviewedAt == null;
            long reviewAgeHours = reviewedAt != null
                    ? Math.max(0L, Duration.between(reviewedAt, config.generatedAt()).toHours())
                    : -1L;
            boolean reviewFresh = reviewedAt != null && reviewAgeHours <= config.reviewTtlHours();
            String deprecationReason = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("deprecation_reason")));
            Map<String, Object> usage = dialogMacroGovernanceSupportService.loadMacroTemplateUsage(templateId, templateName, config.usageWindowDays());
            long usageCount = dialogMacroGovernanceConfigService.toLong(usage.get("usage_count"));
            long previewCount = dialogMacroGovernanceConfigService.toLong(usage.get("preview_count"));
            long errorCount = dialogMacroGovernanceConfigService.toLong(usage.get("error_count"));
            String lastUsedAt = dialogMacroGovernanceConfigService.normalizeUtcTimestamp(usage.get("last_used_at"));
            OffsetDateTime lastUsedAtUtc = dialogMacroGovernanceConfigService.parseReviewTimestamp(lastUsedAt);
            String deprecatedAtRaw = dialogMacroGovernanceConfigService.normalizeNullString(String.valueOf(template.get("deprecated_at")));
            OffsetDateTime deprecatedAtUtc = dialogMacroGovernanceConfigService.parseReviewTimestamp(deprecatedAtRaw);
            List<String> tagAliases = dialogMacroGovernanceSupportService.resolveMacroTagAliases(template.get("tags"));
            int duplicateAliasCount = Math.max(0, tagAliases.size() - new LinkedHashSet<>(tagAliases).size());
            if (duplicateAliasCount == 0 && tagAliases.size() > 1) {
                duplicateAliasCount = 1;
            }
            List<String> usedVariables = dialogMacroGovernanceSupportService.extractMacroTemplateVariables(templateText);
            List<String> unknownVariables = usedVariables.stream()
                    .filter(variable -> !config.knownMacroVariables().contains(variable))
                    .distinct()
                    .toList();
            String usageTier = dialogMacroGovernanceSupportService.resolveMacroUsageTier(
                    usageCount,
                    config.usageTierLowMax(),
                    config.usageTierMediumMax()
            );
            int cleanupSlaDays = dialogMacroGovernanceSupportService.resolveMacroTierSlaDays(
                    usageTier,
                    config.cleanupSlaLowDays(),
                    config.cleanupSlaMediumDays(),
                    config.cleanupSlaHighDays()
            );
            int deprecationSlaDays = dialogMacroGovernanceSupportService.resolveMacroTierSlaDays(
                    usageTier,
                    config.deprecationSlaLowDays(),
                    config.deprecationSlaMediumDays(),
                    config.deprecationSlaHighDays()
            );
            OffsetDateTime cleanupReferenceAt = lastUsedAtUtc != null ? lastUsedAtUtc : (reviewedAt != null ? reviewedAt : config.generatedAt());
            long cleanupDueInDays = Duration.between(config.generatedAt(), cleanupReferenceAt.plusDays(cleanupSlaDays)).toDays();
            String cleanupSlaStatus = !activePublished ? "off" : (cleanupDueInDays < 0 ? "hold" : "attention");
            OffsetDateTime deprecationReferenceAt = deprecatedAtUtc != null ? deprecatedAtUtc : config.generatedAt();
            long deprecationDueInDays = Duration.between(config.generatedAt(), deprecationReferenceAt.plusDays(deprecationSlaDays)).toDays();
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
                hasBlockingIssue = config.requireOwner();
                issues.add(buildIssue("owner_missing", templateId, templateName,
                        config.requireOwner() ? "hold" : "attention",
                        config.requireOwner() ? "rollout_blocker" : "backlog_candidate",
                        "У опубликованного макроса отсутствует owner.",
                        "owner=missing"));
            }
            if (activePublished && !StringUtils.hasText(namespace)) {
                missingNamespaceTotal += 1;
                templateIssues.add("namespace_missing");
                hasBlockingIssue = hasBlockingIssue || config.requireNamespace();
                issues.add(buildIssue("namespace_missing", templateId, templateName,
                        config.requireNamespace() ? "hold" : "attention",
                        config.requireNamespace() ? "rollout_blocker" : "backlog_candidate",
                        "У опубликованного макроса отсутствует namespace.",
                        "namespace=missing"));
            }
            if (activePublished && reviewedAtInvalid) {
                invalidReviewTotal += 1;
                templateIssues.add("review_invalid_utc");
                hasBlockingIssue = hasBlockingIssue || config.requireReview();
                issues.add(buildIssue("review_invalid_utc", templateId, templateName,
                        config.requireReview() ? "hold" : "attention",
                        config.requireReview() ? "rollout_blocker" : "backlog_candidate",
                        "Дата review макроса невалидна и не может быть интерпретирована как UTC.",
                        "reviewed_at=invalid"));
            }
            if (activePublished && config.requireReview() && (!reviewFresh || reviewedAt == null || reviewedAtInvalid)) {
                staleReviewTotal += 1;
                templateIssues.add(reviewedAt == null ? "review_missing" : "review_stale");
                hasBlockingIssue = true;
                issues.add(buildIssue(
                        reviewedAt == null ? "review_missing" : "review_stale",
                        templateId,
                        templateName,
                        "hold",
                        "rollout_blocker",
                        reviewedAt == null ? "У опубликованного макроса нет review-signoff." : "Review макроса устарел и требует повторной проверки.",
                        reviewedAt == null ? "reviewed_at=missing" : "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, config.reviewTtlHours())));
            }
            if (activePublished && usageCount <= 0) {
                unusedPublishedTotal += 1;
                templateIssues.add("unused_recently");
                issues.add(buildIssue("unused_recently", templateId, templateName, "attention", "backlog_candidate",
                        "Опубликованный макрос не использовался в telemetry окне и требует cleanup-review.",
                        "window_days=%d".formatted(config.usageWindowDays())));
            }

            List<String> redListReasons = new ArrayList<>();
            if (config.redListEnabled() && activePublished && usageCount <= config.redListUsageMax()) {
                redListReasons.add("low_adoption");
            }
            if (config.redListEnabled() && activePublished && previewCount > 0 && usageCount == 0) {
                redListReasons.add("preview_only");
            }
            if (config.redListEnabled() && activePublished && errorCount > 0) {
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
                issues.add(buildIssue("red_list_candidate", templateId, templateName,
                        config.ownerActionRequired() ? "hold" : "attention",
                        config.ownerActionRequired() ? "rollout_blocker" : "backlog_candidate",
                        "Макрос попал в quality red-list и требует owner review.",
                        "reasons=%s".formatted(String.join(",", redListReasons))));
            }
            if (config.aliasCleanupRequired() && activePublished && duplicateAliasCount > 0) {
                aliasCleanupTotal += 1;
                templateIssues.add("alias_cleanup_required");
                issues.add(buildIssue("alias_cleanup_required", templateId, templateName, "attention", "backlog_candidate",
                        "У macro template есть дублирующиеся aliases/tags и нужен cleanup.",
                        "duplicate_aliases=%d".formatted(duplicateAliasCount)));
            }
            if (config.variableCleanupRequired() && activePublished && !unknownVariables.isEmpty()) {
                variableCleanupTotal += 1;
                templateIssues.add("unknown_variables_detected");
                issues.add(buildIssue("unknown_variables_detected", templateId, templateName, "attention", "backlog_candidate",
                        "В macro template есть переменные вне известного каталога.",
                        "unknown_variables=%s".formatted(String.join(",", unknownVariables))));
            }

            boolean ownerActionNeeded = config.ownerActionRequired()
                    && activePublished
                    && (redListCandidate
                    || (config.aliasCleanupRequired() && duplicateAliasCount > 0)
                    || (config.variableCleanupRequired() && !unknownVariables.isEmpty()));
            long ownerActionDueInDays = Long.MIN_VALUE;
            String ownerActionStatus = "off";
            if (ownerActionNeeded) {
                ownerActionTotal += 1;
                OffsetDateTime dueReference = lastUsedAtUtc != null ? lastUsedAtUtc : reviewedAt;
                if (!StringUtils.hasText(owner)) {
                    ownerActionStatus = "hold";
                } else if (config.cleanupCadenceDays() <= 0) {
                    ownerActionStatus = "attention";
                } else if (dueReference == null) {
                    ownerActionStatus = "hold";
                } else {
                    ownerActionDueInDays = Duration.between(config.generatedAt(), dueReference.plusDays(config.cleanupCadenceDays())).toDays();
                    ownerActionStatus = ownerActionDueInDays < 0 ? "hold" : "attention";
                }
                templateIssues.add("owner_action_required");
                issues.add(buildIssue(
                        "owner_action_required",
                        templateId,
                        templateName,
                        "hold".equals(ownerActionStatus) ? "hold" : "attention",
                        "hold".equals(ownerActionStatus) ? "rollout_blocker" : "backlog_candidate",
                        "Для проблемного macro template требуется owner action.",
                        config.cleanupCadenceDays() > 0
                                ? (ownerActionDueInDays == Long.MIN_VALUE ? "owner_action_due=unknown" : "owner_action_due_in_days=%d".formatted(ownerActionDueInDays))
                                : "owner_action_required"));
            }

            if (deprecated && config.deprecationRequiresReason() && !StringUtils.hasText(deprecationReason)) {
                deprecationGapTotal += 1;
                templateIssues.add("deprecation_reason_missing");
                issues.add(buildIssue("deprecation_reason_missing", templateId, templateName, "attention", "backlog_candidate",
                        "Для deprecated макроса не указана причина вывода из эксплуатации.",
                        "deprecation_reason=missing"));
            }
            if (config.usageTierSlaRequired() && activePublished && cleanupDueInDays < 0) {
                cleanupSlaOverdueTotal += 1;
                templateIssues.add("cleanup_sla_overdue");
                issues.add(buildIssue("cleanup_sla_overdue", templateId, templateName, "hold", "rollout_blocker",
                        "Cleanup SLA для macro template просрочен.",
                        "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(cleanupDueInDays))));
            }
            if (config.usageTierSlaRequired() && deprecated && deprecationDueInDays < 0) {
                deprecationSlaOverdueTotal += 1;
                templateIssues.add("deprecation_sla_overdue");
                issues.add(buildIssue("deprecation_sla_overdue", templateId, templateName, "hold", "rollout_blocker",
                        "Deprecation SLA для macro template просрочен.",
                        "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(deprecationDueInDays))));
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

        return new TemplateAuditBundle(
                auditedTemplates,
                issues,
                publishedActiveTotal,
                deprecatedTotal,
                missingOwnerTotal,
                missingNamespaceTotal,
                staleReviewTotal,
                invalidReviewTotal,
                unusedPublishedTotal,
                deprecationGapTotal,
                redListTotal,
                lowSignalRedListTotal,
                ownerActionTotal,
                aliasCleanupTotal,
                variableCleanupTotal,
                cleanupSlaOverdueTotal,
                deprecationSlaOverdueTotal,
                lowSignalRedListTemplates
        );
    }

    private Map<String, Object> buildIssue(String type,
                                           String templateId,
                                           String templateName,
                                           String status,
                                           String classification,
                                           String summary,
                                           String detail) {
        return dialogMacroGovernanceSupportService.buildMacroGovernanceIssue(
                type,
                templateId,
                templateName,
                status,
                classification,
                summary,
                detail
        );
    }

    public record TemplateAuditBundle(List<Map<String, Object>> auditedTemplates,
                                      List<Map<String, Object>> issues,
                                      int publishedActiveTotal,
                                      int deprecatedTotal,
                                      int missingOwnerTotal,
                                      int missingNamespaceTotal,
                                      int staleReviewTotal,
                                      int invalidReviewTotal,
                                      int unusedPublishedTotal,
                                      int deprecationGapTotal,
                                      int redListTotal,
                                      int lowSignalRedListTotal,
                                      int ownerActionTotal,
                                      int aliasCleanupTotal,
                                      int variableCleanupTotal,
                                      int cleanupSlaOverdueTotal,
                                      int deprecationSlaOverdueTotal,
                                      List<String> lowSignalRedListTemplates) {
    }
}
