package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleValueParserServiceTest {

    private final SlaRoutingRuleValueParserService service = new SlaRoutingRuleValueParserService();

    @Test
    void parsesCandidateCategoriesAndAssigneePoolWithNormalization() {
        assertEquals(Set.of("billing", "vip"), service.parseCandidateCategories("Billing, VIP"));
        assertEquals(List.of("duty_a", "duty_b"), service.parseAssigneePool(List.of("duty_a", "duty_b", "duty_a")));
    }

    @Test
    void parsesOptionalValuesAndUtcFallbacks() {
        assertEquals(12, service.parseOptionalNonNegativeInt("12"));
        assertNull(service.parseOptionalNonNegativeInt("-1"));
        assertEquals(42L, service.parseOptionalLong("42"));
        assertEquals("domain", service.normalizeRuleLayer("team"));
        assertTrue(service.parseUtcInstant("2026-05-01T10:00:00Z") != null);
        assertNull(service.parseUtcInstant("broken"));
    }
}
