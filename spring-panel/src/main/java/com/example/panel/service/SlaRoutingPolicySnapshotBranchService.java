package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotBranchService {

    private final SlaRoutingPolicySnapshotStateService snapshotStateService;

    public SlaRoutingPolicySnapshotBranchService(SlaRoutingPolicySnapshotStateService snapshotStateService) {
        this.snapshotStateService = snapshotStateService;
    }

    public SlaRoutingPolicySnapshotBranchService() {
        this(new SlaRoutingPolicySnapshotStateService());
    }

    public Map<String, Object> resolveEarlyExit(SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context) {
        if (context == null) {
            return Map.of();
        }
        if (context.missingTicket()) {
            return snapshotStateService.buildTicketMissingPayload();
        }
        if (!context.orchestrationEnabled()) {
            return snapshotStateService.buildDisabledPayload();
        }
        if (context.minutesLeft() == null) {
            return snapshotStateService.buildInvalidUtcPayload();
        }
        if (!context.openLifecycle()) {
            return snapshotStateService.buildNotApplicablePayload();
        }
        if (!context.critical()) {
            return snapshotStateService.buildMonitorPayload();
        }
        return Map.of();
    }
}
