package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingPolicyCandidateBuilderServiceTest {

    private final SlaRoutingPolicyCandidateBuilderService service = new SlaRoutingPolicyCandidateBuilderService();

    @Test
    void buildCandidateMarksBreachedAssignedScope() {
        Map<String, Object> candidate = service.buildCandidate(dialog("T-1", "alice"), -5L, "alice");

        assertEquals("breached", candidate.get("sla_state"));
        assertEquals("assigned", candidate.get("escalation_scope"));
        assertEquals("alice", candidate.get("responsible"));
    }

    private DialogListItem dialog(String ticketId, String responsible) {
        return new DialogListItem(
                ticketId,
                100L,
                1L,
                "client",
                "Client",
                "biz",
                10L,
                "Telegram",
                "Moscow",
                "HQ",
                "Issue",
                "2026-05-05T08:00:00Z",
                "open",
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                "2026-05-05T08:00:00Z",
                0,
                null,
                null
        );
    }
}
