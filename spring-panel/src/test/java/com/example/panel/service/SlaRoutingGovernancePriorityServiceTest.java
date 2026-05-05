package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernancePriorityServiceTest {

    private final SlaRoutingGovernancePriorityService service = new SlaRoutingGovernancePriorityService();

    @Test
    void evaluateReturnsMonitorWhenCheapPathIsStable() {
        SlaRoutingGovernancePriorityService.PrioritySummary summary = service.evaluate(
                List.of("utc_review"),
                1,
                0,
                1,
                0,
                1,
                new SlaRoutingGovernanceLeadTimeService.LeadTimeSummary("controlled", "cheap", "lead", "controlled"),
                true,
                true
        );

        assertEquals("monitor", summary.weeklyReviewPriority());
        assertTrue(summary.minimumRequiredReviewPathReady());
    }

    @Test
    void evaluateReturnsReducePolicyChurnForHighNoiseAndRisk() {
        SlaRoutingGovernancePriorityService.PrioritySummary summary = service.evaluate(
                List.of(),
                0,
                3,
                1,
                4,
                5,
                new SlaRoutingGovernanceLeadTimeService.LeadTimeSummary("high", "aging", "lead", "high"),
                false,
                false
        );

        assertEquals("reduce_policy_churn", summary.weeklyReviewPriority());
        assertEquals("high", summary.advisoryCheckpointLoadLevel());
        assertTrue(summary.advisoryPathReductionCandidate());
    }
}
