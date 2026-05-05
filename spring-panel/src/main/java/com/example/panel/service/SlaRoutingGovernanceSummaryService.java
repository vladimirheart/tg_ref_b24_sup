package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceSummaryService {

    private final SlaRoutingGovernanceReviewService governanceReviewService;
    private final SlaRoutingGovernanceCheckpointService checkpointService;
    private final SlaRoutingGovernanceAuditPayloadAssemblerService payloadAssemblerService;

    @Autowired
    public SlaRoutingGovernanceSummaryService(SlaRoutingGovernanceReviewService governanceReviewService,
                                              SlaRoutingGovernanceCheckpointService checkpointService,
                                              SlaRoutingGovernanceAuditPayloadAssemblerService payloadAssemblerService) {
        this.governanceReviewService = governanceReviewService;
        this.checkpointService = checkpointService;
        this.payloadAssemblerService = payloadAssemblerService;
    }

    public SlaRoutingGovernanceSummaryService() {
        this(
                new SlaRoutingGovernanceReviewService(),
                new SlaRoutingGovernanceCheckpointService(),
                new SlaRoutingGovernanceAuditPayloadAssemblerService()
        );
    }

    public Map<String, Object> buildRoutingGovernanceAuditPayload(String generatedAtUtc,
                                                                  boolean orchestrationEnabled,
                                                                  boolean autoAssignEnabled,
                                                                  String orchestrationMode,
                                                                  boolean includeAssigned,
                                                                  int criticalCandidates,
                                                                  int broadCoveragePct,
                                                                  boolean requireLayers,
                                                                  boolean requireOwner,
                                                                  boolean requireReview,
                                                                  boolean blockOnConflict,
                                                                  long reviewTtlHours,
                                                                  SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis,
                                                                  SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview) {
        List<Map<String, Object>> issues = new ArrayList<>(analysis.issues());
        issues.addAll(governanceReview.issues());
        List<Map<String, Object>> rules = analysis.rules();

        boolean hasHoldIssues = issues.stream().anyMatch(issue -> "hold".equals(String.valueOf(issue.get("status"))));
        boolean hasAttentionIssues = issues.stream().anyMatch(issue -> "attention".equals(String.valueOf(issue.get("status"))));
        String status = !orchestrationEnabled || !autoAssignEnabled
                ? (analysis.rulesTotal() == 0 ? "off" : "attention")
                : hasHoldIssues
                ? "hold"
                : hasAttentionIssues
                ? "attention"
                : analysis.rulesTotal() == 0 ? "off" : "ok";

        String summary = analysis.rulesTotal() == 0
                ? (autoAssignEnabled
                ? "SLA auto-assign включён без явных routing rules: audit остаётся в informational режиме."
                : "SLA routing audit неактивен: auto-assign rules не заданы.")
                : "ok".equals(status)
                ? "SLA routing rules проходят audit без блокирующих сигналов."
                : "hold".equals(status)
                ? "SLA routing audit нашёл блокирующие governance-gap сигналы."
                : "SLA routing audit нашёл non-blocking сигналы, которые стоит убрать до роста конфигурационного долга.";

        long mandatoryIssueTotal = issues.stream().filter(issue -> "rollout_blocker".equals(String.valueOf(issue.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long conflictIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("conflict")).count();
        long reviewIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("review") || String.valueOf(issue.get("type")).contains("decision")).count();
        long ownershipIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("owner") || String.valueOf(issue.get("type")).contains("layer")).count();

        SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary checkpointSummary = checkpointService.evaluate(
                governanceReview,
                requireLayers,
                requireOwner,
                requireReview,
                blockOnConflict,
                analysis.conflictingRulesCount(),
                mandatoryIssueTotal,
                advisoryIssueTotal,
                ownershipIssueTotal,
                issues.size()
        );

        Map<String, Object> requirementsPayload = governanceReviewService.buildRequirementsPayload(
                governanceReview,
                requireLayers,
                requireOwner,
                requireReview,
                reviewTtlHours,
                blockOnConflict,
                broadCoveragePct,
                (int) analysis.conflictingRulesCount()
        );
        Map<String, Object> governanceReviewPayload = governanceReviewService.buildGovernanceReviewPayload(
                governanceReview,
                (int) analysis.conflictingRulesCount(),
                (int) analysis.conflictingTicketsCount()
        );

        return payloadAssemblerService.assemble(
                generatedAtUtc,
                status,
                summary,
                orchestrationEnabled,
                autoAssignEnabled,
                orchestrationMode,
                includeAssigned,
                criticalCandidates,
                analysis,
                mandatoryIssueTotal,
                advisoryIssueTotal,
                conflictIssueTotal,
                reviewIssueTotal,
                ownershipIssueTotal,
                checkpointSummary,
                requirementsPayload,
                governanceReviewPayload,
                issues,
                rules
        );
    }
}
