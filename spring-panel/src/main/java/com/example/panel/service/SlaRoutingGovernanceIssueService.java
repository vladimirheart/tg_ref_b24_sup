package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingGovernanceIssueService {

    public RuleGovernanceEvaluation evaluateRule(SlaRoutingRuleParserService.AutoAssignRuleDefinition definition,
                                                 int matchedCount,
                                                 int selectedCount,
                                                 double coverageRate,
                                                 boolean broadRule,
                                                 boolean requireLayers,
                                                 boolean requireOwner,
                                                 boolean requireReview,
                                                 boolean blockOnConflict,
                                                 long reviewTtlHours,
                                                 Instant generatedAt,
                                                 boolean hasConflict,
                                                 Set<String> conflictTickets,
                                                 Set<String> tiedRoutes,
                                                 String assigneeTarget) {
        boolean unusedRule = matchedCount == 0;
        boolean missingLayer = "legacy".equals(definition.layer());
        boolean ownerMissing = trimToNull(definition.owner()) == null;
        boolean reviewMissing = definition.reviewedAtUtc() == null;
        boolean reviewStale = definition.reviewedAtUtc() != null
                && definition.reviewedAtUtc().plus(Duration.ofHours(reviewTtlHours)).isBefore(generatedAt);

        List<String> ruleIssues = new ArrayList<>();
        List<Map<String, Object>> emittedIssues = new ArrayList<>();
        if (hasConflict) {
            ruleIssues.add("ambiguous_overlap");
            emittedIssues.add(buildGovernanceIssue("rollout_blocker", blockOnConflict ? "hold" : "attention", "rule_conflict",
                    definition.ruleId(), "Обнаружен конфликт SLA-routing правил с одинаковым приоритетом/specificity.",
                    "tickets=%d".formatted(conflictTickets == null ? 0 : conflictTickets.size()),
                    conflictTickets == null ? List.of() : conflictTickets.stream().limit(3).toList(),
                    tiedRoutes == null ? List.of() : tiedRoutes.stream().limit(3).toList()));
        }
        if (broadRule) {
            ruleIssues.add("too_broad");
            emittedIssues.add(buildGovernanceIssue("backlog_candidate", "attention", "broad_rule", definition.ruleId(),
                    "Правило покрывает слишком большой процент критичных кейсов и рискует стать catch-all.",
                    "coverage=%.1f%%".formatted(coverageRate * 100d), List.of(), List.of(definition.layer())));
        }
        if (unusedRule) {
            ruleIssues.add("unused");
            emittedIssues.add(buildGovernanceIssue("backlog_candidate", "attention", "unused_rule", definition.ruleId(),
                    "Правило не матчится ни на один критичный кейс и выглядит как конфигурационный долг.",
                    "matched=0", List.of(), List.of(definition.layer())));
        }
        if (requireLayers && missingLayer) {
            ruleIssues.add("layer_missing");
            emittedIssues.add(buildGovernanceIssue("backlog_candidate", "attention", "layer_missing", definition.ruleId(),
                    "Для production governance правило должно быть отнесено к layer: global/domain/emergency_override.",
                    "layer=legacy", List.of(), List.of()));
        }
        if (requireOwner && ownerMissing) {
            ruleIssues.add("owner_missing");
            emittedIssues.add(buildGovernanceIssue("backlog_candidate", "attention", "owner_missing", definition.ruleId(),
                    "У SLA-routing правила должен быть назначен owner для ревизии и cleanup.",
                    "owner=missing", List.of(), List.of()));
        }
        if (definition.reviewedAtInvalid()) {
            ruleIssues.add("reviewed_at_invalid_utc");
            emittedIssues.add(buildGovernanceIssue("rollout_blocker", requireReview ? "hold" : "attention", "review_invalid_utc",
                    definition.ruleId(), "Поле reviewed_at у SLA-routing правила невалидно и должно быть задано в UTC.",
                    "reviewed_at=invalid", List.of(), List.of()));
        } else if (requireReview && reviewMissing) {
            ruleIssues.add("review_missing");
            emittedIssues.add(buildGovernanceIssue("rollout_blocker", "hold", "review_missing", definition.ruleId(),
                    "Для routing governance нужен review timestamp в UTC.", "reviewed_at=missing", List.of(), List.of()));
        } else if (requireReview && reviewStale) {
            ruleIssues.add("review_stale");
            emittedIssues.add(buildGovernanceIssue("rollout_blocker", "hold", "review_stale", definition.ruleId(),
                    "Review SLA-routing правила устарел и должен быть обновлён.", "ttl_hours=%d".formatted(reviewTtlHours),
                    List.of(), List.of()));
        }

        String status = "ok";
        if (ruleIssues.contains("review_missing") || ruleIssues.contains("review_stale")
                || ruleIssues.contains("reviewed_at_invalid_utc") || (blockOnConflict && hasConflict)) {
            status = "hold";
        } else if (!ruleIssues.isEmpty()) {
            status = "attention";
        }

        Map<String, Object> rulePayload = new LinkedHashMap<>();
        rulePayload.put("rule_id", definition.ruleId());
        rulePayload.put("layer", definition.layer());
        rulePayload.put("owner", definition.owner() == null ? "" : definition.owner());
        rulePayload.put("reviewed_at_utc", definition.reviewedAtUtc() != null ? definition.reviewedAtUtc().toString() : "");
        rulePayload.put("reviewed_at_invalid_utc", definition.reviewedAtInvalid());
        rulePayload.put("priority", definition.rule().priority());
        rulePayload.put("specificity_score", definition.rule().specificityScore());
        rulePayload.put("matched_candidates", matchedCount);
        rulePayload.put("selected_candidates", selectedCount);
        rulePayload.put("coverage_rate", coverageRate);
        rulePayload.put("route", definition.rule().route());
        rulePayload.put("assignee_target", assigneeTarget == null ? "" : assigneeTarget);
        rulePayload.put("issues", ruleIssues);
        rulePayload.put("status", status);

        return new RuleGovernanceEvaluation(rulePayload, emittedIssues);
    }

    public Map<String, Object> buildGovernanceIssue(String classification,
                                                    String status,
                                                    String type,
                                                    String ruleId,
                                                    String summary,
                                                    String detail,
                                                    List<String> ticketIds,
                                                    List<String> relatedRulesOrMeta) {
        return Map.of(
                "classification", classification,
                "status", status,
                "type", type,
                "rule_id", ruleId,
                "summary", summary,
                "detail", detail == null ? "" : detail,
                "tickets", ticketIds == null ? List.of() : ticketIds,
                "related", relatedRulesOrMeta == null ? List.of() : relatedRulesOrMeta
        );
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    public record RuleGovernanceEvaluation(Map<String, Object> rulePayload,
                                           List<Map<String, Object>> emittedIssues) {
    }
}
