package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingPolicyDecisionService {

    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;
    private final SlaRoutingPolicyCandidateBuilderService candidateBuilderService;
    private final SlaRoutingPolicyDecisionPayloadService payloadService;

    @Autowired
    public SlaRoutingPolicyDecisionService(SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                           SlaRoutingPolicyCandidateBuilderService candidateBuilderService,
                                           SlaRoutingPolicyDecisionPayloadService payloadService) {
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
        this.candidateBuilderService = candidateBuilderService;
        this.payloadService = payloadService;
    }

    public SlaRoutingPolicyDecisionService() {
        this(
                new SlaEscalationAutoAssignService(null),
                new SlaRoutingPolicyCandidateBuilderService(),
                new SlaRoutingPolicyDecisionPayloadService()
        );
    }

    public Map<String, Object> buildCriticalSnapshotDecision(DialogListItem dialog,
                                                             Map<String, Object> dialogConfig,
                                                             Long minutesLeft,
                                                             String currentResponsible,
                                                             boolean autoAssignEnabled,
                                                             boolean webhookEnabled,
                                                             SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                                             boolean effectiveIncludeAssigned) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> candidate = candidateBuilderService.buildCandidate(dialog, minutesLeft, currentResponsible);
        if (currentResponsible != null && !effectiveIncludeAssigned) {
            payload.putAll(payloadService.buildAssignedReassignBlockedPayload(orchestrationMode));
            return payload;
        }

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions =
                slaEscalationAutoAssignService.resolveAutoAssignDecisions(List.of(candidate), dialogConfig);
        SlaEscalationWebhookNotifier.AutoAssignDecision decision = decisions.isEmpty() ? null : decisions.get(0);
        if (decision != null) {
            payload.putAll(payloadService.buildDecisionReadyPayload(decision, candidate.get("escalation_scope"),
                    currentResponsible, webhookEnabled, orchestrationMode));
            return payload;
        }

        if (!autoAssignEnabled && webhookEnabled) {
            payload.putAll(payloadService.buildWebhookOnlyPayload(candidate.get("escalation_scope")));
            return payload;
        }

        payload.putAll(payloadService.buildManualReviewPayload(dialogConfig, autoAssignEnabled, candidate.get("escalation_scope")));
        return payload;
    }
}
