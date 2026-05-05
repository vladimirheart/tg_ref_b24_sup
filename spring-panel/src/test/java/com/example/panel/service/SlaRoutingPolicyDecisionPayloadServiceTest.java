package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicyDecisionPayloadServiceTest {

    private final SlaRoutingPolicyDecisionPayloadService service = new SlaRoutingPolicyDecisionPayloadService();

    @Test
    void buildWebhookOnlyPayloadProducesReadyNotifyState() {
        Map<String, Object> payload = service.buildWebhookOnlyPayload("unassigned");

        assertEquals("ready", payload.get("status"));
        assertEquals("notify", payload.get("action"));
        assertTrue(((java.util.List<?>) payload.get("issues")).isEmpty());
    }

    @Test
    void buildManualReviewPayloadAddsFallbackIssue() {
        Map<String, Object> payload = service.buildManualReviewPayload(Map.of(), false, "unassigned");

        assertEquals("attention", payload.get("status"));
        assertFalse((Boolean) payload.get("ready"));
        assertEquals(java.util.List.of("auto_assign_disabled", "fallback_assignee_missing"), payload.get("issues"));
    }
}
