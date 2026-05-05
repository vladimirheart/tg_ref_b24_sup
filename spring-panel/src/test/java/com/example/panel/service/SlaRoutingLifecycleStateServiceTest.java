package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingLifecycleStateServiceTest {

    private final SlaRoutingLifecycleStateService service = new SlaRoutingLifecycleStateService();

    @Test
    void normalizeLifecycleStateMapsOpenClosedAndLegacy() {
        assertEquals("open", service.normalizeLifecycleState("waiting_operator"));
        assertEquals("closed", service.normalizeLifecycleState("resolved"));
        assertEquals("legacy", service.normalizeLifecycleState("legacy"));
        assertEquals("unknown", service.normalizeLifecycleState((String) null));
    }
}
