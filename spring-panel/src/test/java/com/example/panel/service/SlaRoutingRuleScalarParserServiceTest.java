package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleScalarParserServiceTest {

    private final SlaRoutingRuleScalarParserService service = new SlaRoutingRuleScalarParserService();

    @Test
    void parsesOptionalValuesAndUtcFallbacks() {
        assertEquals(12, service.parseOptionalNonNegativeInt("12"));
        assertNull(service.parseOptionalNonNegativeInt("-1"));
        assertEquals(42L, service.parseOptionalLong("42"));
        assertEquals(100, service.parsePriority("999"));
        assertTrue(service.parseUtcInstant("2026-05-01T10:00:00Z") != null);
        assertNull(service.parseUtcInstant("broken"));
    }

    @Test
    void trimsAndParsesOptionalBoolean() {
        assertEquals(Boolean.TRUE, service.parseOptionalBoolean("yes"));
        assertEquals(Boolean.FALSE, service.parseOptionalBoolean("0"));
        assertNull(service.parseOptionalBoolean("maybe"));
        assertNull(service.trimToNull(" null "));
        assertEquals("ops.lead", service.trimToNull(" ops.lead "));
    }
}
