package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SlaRoutingPolicyTimeServiceTest {

    private final SlaRoutingPolicyTimeService service = new SlaRoutingPolicyTimeService();

    @Test
    void normalizeUtcTimestampAndResolveMinutesLeft() {
        assertEquals("2026-05-01T10:00:00Z", service.normalizeUtcTimestamp("2026-05-01T10:00:00+00:00"));
        assertNull(service.normalizeUtcTimestamp("broken"));
        assertEquals(60L, service.resolveMinutesLeft("2026-05-01T10:00:00Z", 120, Instant.parse("2026-05-01T11:00:00Z").toEpochMilli()));
    }
}
