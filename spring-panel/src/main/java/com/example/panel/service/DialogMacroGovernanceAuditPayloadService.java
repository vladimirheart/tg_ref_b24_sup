package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogMacroGovernanceAuditPayloadService {

    public Map<String, Object> build(DialogMacroGovernanceConfigService.AuditConfig config,
                                     DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle templateAudit,
                                     DialogMacroGovernanceCheckpointService.CheckpointBundle checkpoints) {
        List<Map<String, Object>> issues = new ArrayList<>(templateAudit.issues());
        issues.addAll(checkpoints.issues());

        String status;
        if (config.templates().isEmpty()) {
            status = "off";
        } else if (issues.stream().anyMatch(item -> "hold".equalsIgnoreCase(String.valueOf(item.get("status"))))) {
            status = "hold";
        } else if (!issues.isEmpty()) {
            status = "attention";
        } else {
            status = "ok";
        }

        long mandatoryIssueTotal = issues.stream()
                .filter(item -> "rollout_blocker".equals(String.valueOf(item.get("classification"))))
                .count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long reviewIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("review"))
                .count();
        long ownershipIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("owner")
                        || String.valueOf(item.get("type")).contains("namespace"))
                .count();
        long cleanupIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("cleanup")
                        || String.valueOf(item.get("type")).contains("deprecation")
                        || String.valueOf(item.get("type")).contains("alias")
                        || String.valueOf(item.get("type")).contains("variable"))
                .count();
        long lowSignalAdvisoryTotal = templateAudit.lowSignalRedListTotal();
        long actionableAdvisoryTotal = Math.max(0L, advisoryIssueTotal - lowSignalAdvisoryTotal);
        long advisoryLoadBase = Math.max(1L, templateAudit.publishedActiveTotal());
        long noiseRatioPct = advisoryIssueTotal <= 0L
                ? 0L
                : Math.min(100L, Math.round((advisoryIssueTotal * 100d) / advisoryLoadBase));
        long advisoryNoiseExcludingLowSignalPct = actionableAdvisoryTotal <= 0L
                ? 0L
                : Math.min(100L, Math.round((actionableAdvisoryTotal * 100d) / advisoryLoadBase));
        long actionableAdvisorySharePct = advisoryIssueTotal > 0 ? Math.round((actionableAdvisoryTotal * 100d) / advisoryIssueTotal) : 0L;
        long lowSignalAdvisorySharePct = advisoryIssueTotal > 0 ? Math.round((lowSignalAdvisoryTotal * 100d) / advisoryIssueTotal) : 0L;
        String noiseLevel = noiseRatioPct >= 75L
                ? "high"
                : noiseRatioPct >= 40L ? "moderate" : "controlled";
        boolean advisoryFollowupRequired = actionableAdvisoryTotal > mandatoryIssueTotal || advisoryNoiseExcludingLowSignalPct >= 50L;
        boolean lowSignalBacklogDominant = lowSignalAdvisoryTotal > actionableAdvisoryTotal && lowSignalAdvisorySharePct >= 50L;
        String lowSignalBacklogSummary = lowSignalBacklogDominant
                ? "Low-signal red-list начинает доминировать над actionable advisory; держите его аналитическим, а не backlog-driven."
                : "Low-signal red-list не доминирует над actionable advisory.";
        String weeklyReviewPriority = checkpoints.requiredCheckpointClosureRatePct() < 100L
                ? "close_required_path"
                : checkpoints.freshnessClosureRatePct() < 100L
                ? "refresh_stale_checkpoints"
                : actionableAdvisoryTotal == 0 && lowSignalAdvisoryTotal > 0
                ? "monitor_low_signal_advisories"
                : "high".equals(noiseLevel)
                ? "reduce_advisory_noise"
                : advisoryFollowupRequired ? "trim_advisory_noise" : "monitor";
        String weeklyReviewSummary = switch (weeklyReviewPriority) {
            case "close_required_path" -> "Сначала закройте обязательные macro checkpoints.";
            case "refresh_stale_checkpoints" -> "Освежите review/catalog/deprecation checkpoints по UTC TTL.";
            case "monitor_low_signal_advisories" -> "Advisory шум в основном состоит из low-signal red-list кандидатов; держите их аналитическими, а не бюрократическими.";
            case "reduce_advisory_noise" -> "Сократите advisory red-list шум до минимального обязательного контура.";
            case "trim_advisory_noise" -> "Проверьте, что advisory сигналы не доминируют над обязательными.";
            default -> "Closure, freshness и noise находятся в рабочем диапазоне.";
        };
        boolean weeklyReviewFollowupRequired = !"monitor".equals(weeklyReviewPriority)
                && !"monitor_low_signal_advisories".equals(weeklyReviewPriority);
        boolean advisoryPathReductionCandidate = "reduce_advisory_noise".equals(weeklyReviewPriority)
                || "trim_advisory_noise".equals(weeklyReviewPriority);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generated_at", config.generatedAt().toInstant().toString());
        audit.put("status", status);
        audit.put("summary", config.templates().isEmpty()
                ? "Macro governance audit недоступен: макросы не настроены."
                : "Published active=%d, deprecated=%d, issues=%d, red-list=%d.".formatted(
                        templateAudit.publishedActiveTotal(),
                        templateAudit.deprecatedTotal(),
                        issues.size(),
                        templateAudit.redListTotal()));
        audit.put("templates_total", config.templates().size());
        audit.put("published_active_total", templateAudit.publishedActiveTotal());
        audit.put("deprecated_total", templateAudit.deprecatedTotal());
        audit.put("issues_total", issues.size());
        audit.put("mandatory_issue_total", mandatoryIssueTotal);
        audit.put("advisory_issue_total", advisoryIssueTotal);
        audit.put("low_signal_advisory_total", lowSignalAdvisoryTotal);
        audit.put("actionable_advisory_total", actionableAdvisoryTotal);
        audit.put("actionable_advisory_share_pct", actionableAdvisorySharePct);
        audit.put("low_signal_advisory_share_pct", lowSignalAdvisorySharePct);
        audit.put("low_signal_backlog_dominant", lowSignalBacklogDominant);
        audit.put("low_signal_backlog_summary", lowSignalBacklogSummary);
        audit.put("missing_owner_total", templateAudit.missingOwnerTotal());
        audit.put("missing_namespace_total", templateAudit.missingNamespaceTotal());
        audit.put("stale_review_total", templateAudit.staleReviewTotal());
        audit.put("invalid_review_total", templateAudit.invalidReviewTotal());
        audit.put("unused_published_total", templateAudit.unusedPublishedTotal());
        audit.put("deprecation_gap_total", templateAudit.deprecationGapTotal());
        audit.put("red_list_total", templateAudit.redListTotal());
        audit.put("owner_action_total", templateAudit.ownerActionTotal());
        audit.put("alias_cleanup_total", templateAudit.aliasCleanupTotal());
        audit.put("variable_cleanup_total", templateAudit.variableCleanupTotal());
        audit.put("cleanup_sla_overdue_total", templateAudit.cleanupSlaOverdueTotal());
        audit.put("deprecation_sla_overdue_total", templateAudit.deprecationSlaOverdueTotal());
        audit.put("minimum_required_checkpoints", checkpoints.minimumRequiredCheckpoints());
        audit.put("required_checkpoint_total", checkpoints.requiredCheckpointTotal());
        audit.put("required_checkpoint_ready_total", checkpoints.requiredCheckpointReadyTotal());
        audit.put("required_checkpoint_closure_rate_pct", checkpoints.requiredCheckpointClosureRatePct());
        audit.put("minimum_required_path_controlled", checkpoints.minimumRequiredPathControlled());
        audit.put("freshness_checkpoint_total", checkpoints.freshnessCheckpointTotal());
        audit.put("freshness_checkpoint_ready_total", checkpoints.freshnessCheckpointReadyTotal());
        audit.put("freshness_closure_rate_pct", checkpoints.freshnessClosureRatePct());
        audit.put("noise_ratio_pct", noiseRatioPct);
        audit.put("advisory_noise_excluding_low_signal_pct", advisoryNoiseExcludingLowSignalPct);
        audit.put("noise_level", noiseLevel);
        audit.put("advisory_followup_required", advisoryFollowupRequired);
        audit.put("weekly_review_priority", weeklyReviewPriority);
        audit.put("weekly_review_summary", weeklyReviewSummary);
        audit.put("weekly_review_followup_required", weeklyReviewFollowupRequired);
        audit.put("advisory_path_reduction_candidate", advisoryPathReductionCandidate);
        audit.put("advisory_signals", checkpoints.advisorySignals());
        audit.put("low_signal_red_list_templates", templateAudit.lowSignalRedListTemplates().stream().distinct().limit(5).toList());
        audit.put("issue_breakdown", Map.of(
                "review", reviewIssueTotal,
                "ownership", ownershipIssueTotal,
                "cleanup", cleanupIssueTotal,
                "mandatory", mandatoryIssueTotal,
                "advisory", advisoryIssueTotal
        ));
        audit.put("requirements", Map.ofEntries(
                Map.entry("require_owner", config.requireOwner()),
                Map.entry("require_namespace", config.requireNamespace()),
                Map.entry("require_review", config.requireReview()),
                Map.entry("review_ttl_hours", config.reviewTtlHours()),
                Map.entry("deprecation_requires_reason", config.deprecationRequiresReason()),
                Map.entry("unused_days", config.usageWindowDays()),
                Map.entry("red_list_enabled", config.redListEnabled()),
                Map.entry("red_list_usage_max", config.redListUsageMax()),
                Map.entry("owner_action_required", config.ownerActionRequired()),
                Map.entry("cleanup_cadence_days", config.cleanupCadenceDays()),
                Map.entry("alias_cleanup_required", config.aliasCleanupRequired()),
                Map.entry("variable_cleanup_required", config.variableCleanupRequired()),
                Map.entry("usage_tier_sla_required", config.usageTierSlaRequired()),
                Map.entry("usage_tier_low_max", config.usageTierLowMax()),
                Map.entry("usage_tier_medium_max", config.usageTierMediumMax()),
                Map.entry("cleanup_sla_low_days", config.cleanupSlaLowDays()),
                Map.entry("cleanup_sla_medium_days", config.cleanupSlaMediumDays()),
                Map.entry("cleanup_sla_high_days", config.cleanupSlaHighDays()),
                Map.entry("deprecation_sla_low_days", config.deprecationSlaLowDays()),
                Map.entry("deprecation_sla_medium_days", config.deprecationSlaMediumDays()),
                Map.entry("deprecation_sla_high_days", config.deprecationSlaHighDays())
        ));
        audit.put("governance_review", checkpoints.governanceReview());
        audit.put("external_catalog_contract", checkpoints.externalCatalogContract());
        audit.put("deprecation_policy", checkpoints.deprecationPolicy());
        audit.put("issues", issues);
        audit.put("templates", templateAudit.auditedTemplates());
        return audit;
    }
}
