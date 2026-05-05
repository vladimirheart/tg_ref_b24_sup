package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotService {

    private final SlaRoutingPolicyDecisionService decisionService;
    private final SlaRoutingPolicySnapshotRuntimeService runtimeService;
    private final SlaRoutingPolicySnapshotBranchService branchService;

    public SlaRoutingPolicySnapshotService(SlaRoutingPolicyConfigService policyConfigService,
                                           SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this(
                new SlaRoutingPolicySnapshotRuntimeService(policyConfigService, new SlaRoutingPolicySnapshotStateService()),
                new SlaRoutingPolicySnapshotBranchService(new SlaRoutingPolicySnapshotStateService()),
                new SlaRoutingPolicyDecisionService(
                        slaEscalationAutoAssignService,
                        new SlaRoutingPolicyCandidateBuilderService(),
                        new SlaRoutingPolicyDecisionPayloadService(
                                new SlaRoutingPolicyPreviewSummaryService(),
                                policyConfigService
                        )
                )
        );
    }

    public SlaRoutingPolicySnapshotService(SlaRoutingPolicySnapshotRuntimeService runtimeService,
                                           SlaRoutingPolicySnapshotBranchService branchService,
                                           SlaRoutingPolicyDecisionService decisionService) {
        this.runtimeService = runtimeService;
        this.branchService = branchService;
        this.decisionService = decisionService;
    }

    public SlaRoutingPolicySnapshotService() {
        this(new SlaRoutingPolicySnapshotRuntimeService(), new SlaRoutingPolicySnapshotBranchService(), new SlaRoutingPolicyDecisionService());
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context =
                runtimeService.build(dialog, settings, java.time.Instant.now());
        Map<String, Object> payload = context.payload();
        Map<String, Object> earlyExit = branchService.resolveEarlyExit(context);
        if (!earlyExit.isEmpty()) {
            payload.putAll(earlyExit);
            return payload;
        }
        payload.putAll(decisionService.buildCriticalSnapshotDecision(
                dialog,
                context.dialogConfig(),
                context.minutesLeft(),
                context.currentResponsible(),
                context.autoAssignEnabled(),
                context.webhookEnabled(),
                context.orchestrationMode(),
                context.effectiveIncludeAssigned()
        ));
        return payload;
    }
}
